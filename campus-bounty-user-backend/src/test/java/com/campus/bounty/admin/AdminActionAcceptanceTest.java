package com.campus.bounty.admin;

import com.campus.bounty.config.JwtUtil;
import com.campus.bounty.notification.Notification;
import com.campus.bounty.notification.NotificationService;
import com.campus.bounty.trade.Appeal;
import com.campus.bounty.trade.AppealService;
import com.campus.bounty.trade.PostStatus;
import com.campus.bounty.trade.TradeOrder;
import com.campus.bounty.trade.TradeOrderRepository;
import com.campus.bounty.trade.TradeOrderService;
import com.campus.bounty.trade.TradePost;
import com.campus.bounty.trade.TradePostRepository;
import com.campus.bounty.trade.TradePostService;
import com.campus.bounty.trade.dto.PublishReq;
import com.campus.bounty.trade.dto.ServicePublishReq;
import com.campus.bounty.user.User;
import com.campus.bounty.user.UserRepository;
import com.campus.bounty.user.UserService;
import com.campus.bounty.wallet.WalletRepository;
import com.campus.bounty.wallet.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 管理端写操作验收（举报处置 / 封禁解封 / 申诉仲裁）。
 * <p>
 * 这里是整个管理端唯一会动钱、唯一会改订单终态的地方，所以断言集中在四件事实：
 * 钱只动一次、方向与金额正确、对账差额守恒、留下唯一一条审计。接口返回 200 本身不算通过。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@SuppressWarnings("unchecked")
class AdminActionAcceptanceTest {
    @LocalServerPort
    int port;
    @Autowired
    TestRestTemplate rest;
    @Autowired
    AdminService adminService;
    @Autowired
    TradePostService tradePostService;
    @Autowired
    TradeOrderService tradeOrderService;
    @Autowired
    TradePostRepository tradePostRepository;
    @Autowired
    TradeOrderRepository tradeOrderRepository;
    @Autowired
    AppealService appealService;
    @Autowired
    WalletService walletService;
    @Autowired
    WalletRepository walletRepository;
    @Autowired
    NotificationService notificationService;
    @Autowired
    UserService userService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    JwtUtil jwtUtil;

    private final AtomicInteger seq = new AtomicInteger();

    @Test
    void report_valid_with_moderation_closes_target_and_leaves_audit() {
        User reporter = register("ar_reporter");
        User author = register("ar_author");
        recharge(author, "50");
        TradePost post = publishBounty(author, "10.00");

        long reportId = submitReport(reporter, "TRADE_POST", post.getId(), "价格虚高，疑似刷单");

        ResponseEntity<Map> handled = rest.postForEntity(
                url("/api/admin/reports/" + reportId + "/handle"),
                new HttpEntity<>(Map.of("result", "VALID", "moderate", true,
                        "comment", "核实存在刷单行为，下架处理"), authHeaders(admin())), Map.class);
        assertEquals(0, ((Number) handled.getBody().get("code")).intValue(),
                "成立并顺带治理应成功：" + handled.getBody().get("message"));

        assertEquals(PostStatus.CLOSED, tradePostRepository.findById(post.getId()).orElseThrow().getStatus(),
                "处置后交易帖必须下架");

        Map<String, Object> detail = (Map<String, Object>) data(
                adminGet("/api/admin/reports/" + reportId));
        assertEquals("HANDLED", detail.get("status"));
        assertEquals("VALID", detail.get("result"));
        assertEquals("CLOSE_TRADE_POST", detail.get("moderationAction"),
                "治理动作必须落库，否则事后无法复盘管理员到底做了什么");
        assertEquals(admin().getId().longValue(), ((Number) detail.get("adminId")).longValue());
        assertNotNull(detail.get("handledAt"));
        assertEquals("核实存在刷单行为，下架处理", detail.get("comment"));

        assertAudit("HANDLE_REPORT", "REPORT", reportId, 1, "处置应留下唯一一条审计");
        assertAudit("CLOSE_TRADE_POST", "TRADE_POST", post.getId(), 1, "治理动作本身也应有独立审计");

        List<Notification> notes = notificationService.list(reporter.getId()).stream()
                .filter(n -> "REPORT_HANDLED".equals(n.getType()) && Long.valueOf(reportId).equals(n.getRefId()))
                .toList();
        assertEquals(1, notes.size(), "举报人应收到一条处理结果通知");

        // 已处理过的举报不能重复处置
        ResponseEntity<Map> again = rest.postForEntity(
                url("/api/admin/reports/" + reportId + "/handle"),
                new HttpEntity<>(Map.of("result", "REJECTED", "comment", "再处理一次"), authHeaders(admin())),
                Map.class);
        assertEquals(40000, ((Number) again.getBody().get("code")).intValue(), "重复处置必须被拒");
    }

    @Test
    void report_rejected_cannot_moderate_and_pending_list_drops_it() {
        User reporter = register("ar2_reporter");
        User author = register("ar2_author");
        recharge(author, "50");
        TradePost post = publishBounty(author, "10.00");
        long reportId = submitReport(reporter, "TRADE_POST", post.getId(), "误报测试");

        ResponseEntity<Map> illegal = rest.postForEntity(
                url("/api/admin/reports/" + reportId + "/handle"),
                new HttpEntity<>(Map.of("result", "REJECTED", "moderate", true,
                        "comment", "驳回却要下架"), authHeaders(admin())), Map.class);
        assertEquals(40000, ((Number) illegal.getBody().get("code")).intValue(),
                "驳回举报时不允许执行治理动作，否则等于无依据处罚");
        assertEquals(PostStatus.OPEN, tradePostRepository.findById(post.getId()).orElseThrow().getStatus(),
                "被拦下后帖子不应被改动");

        ResponseEntity<Map> rejected = rest.postForEntity(
                url("/api/admin/reports/" + reportId + "/handle"),
                new HttpEntity<>(Map.of("result", "REJECTED", "moderate", false,
                        "comment", "证据不足，驳回"), authHeaders(admin())), Map.class);
        assertEquals(0, ((Number) rejected.getBody().get("code")).intValue());

        Map<String, Object> detail = (Map<String, Object>) data(adminGet("/api/admin/reports/" + reportId));
        assertEquals("REJECTED", detail.get("result"));
        Object action = detail.get("moderationAction");
        assertTrue(action == null, "驳回不应产生治理动作，实际为 " + action);
    }

    @Test
    void ban_then_unban_restores_login_capability() {
        User student = register("ar_student");
        HttpHeaders studentHeaders = authHeaders(student);

        assertEquals(HttpStatus.OK, rest.exchange(url("/api/trade/posts"), HttpMethod.GET,
                new HttpEntity<>(studentHeaders), Map.class).getStatusCode(),
                "封禁前这枚 token 必须本来可用，否则后面的 401 证明不了任何事");

        assertEquals(0, ((Number) rest.postForEntity(url("/api/admin/users/" + student.getId() + "/ban"),
                new HttpEntity<>(authHeaders(admin())), Map.class).getBody().get("code")).intValue());
        assertEquals("BANNED", userRepository.findById(student.getId()).orElseThrow().getStatus());
        assertEquals(HttpStatus.UNAUTHORIZED, rest.exchange(url("/api/trade/posts"), HttpMethod.GET,
                new HttpEntity<>(studentHeaders), Map.class).getStatusCode(), "封禁后旧 token 应立即失效");

        assertEquals(0, ((Number) rest.postForEntity(url("/api/admin/users/" + student.getId() + "/unban"),
                new HttpEntity<>(authHeaders(admin())), Map.class).getBody().get("code")).intValue());
        assertEquals("ACTIVE", userRepository.findById(student.getId()).orElseThrow().getStatus());
        assertEquals(HttpStatus.OK, rest.exchange(url("/api/trade/posts"), HttpMethod.GET,
                new HttpEntity<>(studentHeaders), Map.class).getStatusCode(),
                "解封后同一枚 token 应恢复可用（系统未做 token 吊销，这是已知语义）");

        // 管理员账号不可被治理，避免把管理端锁死
        ResponseEntity<Map> banAdmin = rest.postForEntity(url("/api/admin/users/" + admin().getId() + "/ban"),
                new HttpEntity<>(authHeaders(admin())), Map.class);
        assertEquals(40000, ((Number) banAdmin.getBody().get("code")).intValue(), "不允许封禁管理员账号");
    }

    @Test
    void arbitrate_release_pays_seller_and_completes_bounty_order() {
        User buyer = register("aa_buyer");
        User seller = register("aa_seller");
        recharge(buyer, "100");
        TradePost post = publishBounty(buyer, "20.00");
        TradeOrder order = tradeOrderRepository.findByPostId(post.getId()).orElseThrow();
        tradeOrderService.accept(order.getId(), seller.getId());
        Appeal appeal = appealService.open(order.getId(), seller.getId(), "已完成跑腿，对方拒付");

        BigDecimal diffBefore = differenceOf();
        assertEquals(0, new BigDecimal("20.00").compareTo(frozenOf(buyer)), "申诉期间买家应有 20 元托管冻结");

        ResponseEntity<Map> response = rest.postForEntity(
                url("/api/admin/appeals/" + appeal.getId() + "/arbitrate"),
                new HttpEntity<>(Map.of("outcome", "RELEASE_TO_SELLER",
                        "comment", "核实已完成交付，判付"), authHeaders(admin())), Map.class);
        assertEquals(0, ((Number) response.getBody().get("code")).intValue(),
                "判付应成功：" + response.getBody().get("message"));

        assertEquals(0, new BigDecimal("20.00").compareTo(balanceOf(seller)), "卖家应收到全额赏金");
        assertEquals(0, BigDecimal.ZERO.compareTo(frozenOf(buyer)), "托管冻结应清零");
        assertEquals(0, new BigDecimal("80.00").compareTo(balanceOf(buyer)), "买家余额应保持冻结前的 80");

        TradeOrder settled = tradeOrderRepository.findById(order.getId()).orElseThrow();
        assertEquals("COMPLETED", settled.getStatus().name(), "判付后订单应为已完成");
        assertEquals(PostStatus.CLOSED, tradePostRepository.findById(post.getId()).orElseThrow().getStatus(),
                "悬赏帖一帖一单，结束后应关闭");

        Appeal resolved = appealService.viewLatest(order.getId(), buyer.getId());
        assertEquals("RESOLVED", resolved.getStatus());
        assertEquals("RELEASE_TO_SELLER", resolved.getOutcome());
        assertEquals("核实已完成交付，判付", resolved.getAdminComment());
        assertNotNull(resolved.getHandledAt());

        assertAudit("ARBITRATE_APPEAL", "APPEAL", appeal.getId(), 1, "裁决应留下唯一一条审计");
        assertResolvedNotification(seller, order.getId());
        assertResolvedNotification(buyer, order.getId());

        assertEquals(0, diffBefore.compareTo(differenceOf()),
                "裁决只是把冻结挪到余额，平台对账差额不应变化（增量守恒）");

        // 重复裁决：必须被拒，且钱不能再动第二次
        ResponseEntity<Map> twice = rest.postForEntity(
                url("/api/admin/appeals/" + appeal.getId() + "/arbitrate"),
                new HttpEntity<>(Map.of("outcome", "REFUND_TO_BUYER", "comment", "再裁一次"),
                        authHeaders(admin())), Map.class);
        assertEquals(40000, ((Number) twice.getBody().get("code")).intValue(), "已裁决的申诉必须拒绝再次裁决");
        assertEquals(0, new BigDecimal("20.00").compareTo(balanceOf(seller)), "重复裁决后卖家余额必须仍为 20");
        assertEquals(0, BigDecimal.ZERO.compareTo(frozenOf(buyer)), "重复裁决后买家冻结必须仍为 0");
    }

    @Test
    void arbitrate_refund_returns_escrow_and_keeps_service_post_open() {
        User seller = register("aa2_seller");
        User buyer = register("aa2_buyer");
        recharge(buyer, "100");
        TradePost service = publishService(seller, "30.00");
        // 服务单一帖多单：buy 即成交并进入 IN_PROGRESS，不像悬赏那样还有接单环节
        TradeOrder order = tradeOrderService.buy(service.getId(), buyer.getId());
        Appeal appeal = appealService.open(order.getId(), buyer.getId(), "对方未按要求提供服务");

        assertEquals(0, new BigDecimal("30.00").compareTo(frozenOf(buyer)));
        BigDecimal diffBefore = differenceOf();

        ResponseEntity<Map> response = rest.postForEntity(
                url("/api/admin/appeals/" + appeal.getId() + "/arbitrate"),
                new HttpEntity<>(Map.of("outcome", "REFUND_TO_BUYER",
                        "comment", "服务未交付，全额退款"), authHeaders(admin())), Map.class);
        assertEquals(0, ((Number) response.getBody().get("code")).intValue());

        assertEquals(0, BigDecimal.ZERO.compareTo(frozenOf(buyer)), "退款后买家冻结应清零");
        assertEquals(0, new BigDecimal("100.00").compareTo(balanceOf(buyer)), "买家余额应回到充值原值");
        assertEquals(0, BigDecimal.ZERO.compareTo(balanceOf(seller)), "卖家不应收到任何款项");

        assertEquals("CANCELLED", tradeOrderRepository.findById(order.getId()).orElseThrow().getStatus().name(),
                "退款后订单应为已取消");
        assertEquals(PostStatus.OPEN, tradePostRepository.findById(service.getId()).orElseThrow().getStatus(),
                "服务帖一帖多单，一笔纠纷不应下架整帖");

        assertAudit("ARBITRATE_APPEAL", "APPEAL", appeal.getId(), 1, "退款同样要留审计");
        assertEquals(0, diffBefore.compareTo(differenceOf()), "退款也是对账差额守恒的资金动作");
    }

    @Test
    void arbitrate_validation_and_permissions() {
        User buyer = register("av_buyer");
        User seller = register("av_seller");
        User student = register("av_student");
        recharge(buyer, "100");
        TradePost post = publishBounty(buyer, "15.00");
        TradeOrder order = tradeOrderRepository.findByPostId(post.getId()).orElseThrow();
        tradeOrderService.accept(order.getId(), seller.getId());
        Appeal appeal = appealService.open(order.getId(), seller.getId(), "仲裁校验用");

        // 处理意见是硬要求：仲裁必须说明理由，否则事后无法解释钱的去向
        ResponseEntity<Map> blank = rest.postForEntity(
                url("/api/admin/appeals/" + appeal.getId() + "/arbitrate"),
                new HttpEntity<>(Map.of("outcome", "RELEASE_TO_SELLER", "comment", " "), authHeaders(admin())),
                Map.class);
        assertEquals(40000, ((Number) blank.getBody().get("code")).intValue(), "空处理意见必须被拦下");
        assertEquals(0, new BigDecimal("15.00").compareTo(frozenOf(buyer)), "被拦下时不应动钱");

        ResponseEntity<Map> forbidden = rest.postForEntity(
                url("/api/admin/appeals/" + appeal.getId() + "/arbitrate"),
                new HttpEntity<>(Map.of("outcome", "RELEASE_TO_SELLER", "comment", "越权裁决"),
                        authHeaders(student)), Map.class);
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode(), "学生不得调用仲裁");

        ResponseEntity<Map> anonymous = rest.postForEntity(
                url("/api/admin/appeals/" + appeal.getId() + "/arbitrate"),
                new HttpEntity<>(Map.of("outcome", "RELEASE_TO_SELLER", "comment", "匿名")), Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, anonymous.getStatusCode(), "未登录不得调用仲裁");

        ResponseEntity<Map> missing = rest.postForEntity(
                url("/api/admin/appeals/99999999/arbitrate"),
                new HttpEntity<>(Map.of("outcome", "RELEASE_TO_SELLER", "comment", "不存在"),
                        authHeaders(admin())), Map.class);
        assertEquals(40000, ((Number) missing.getBody().get("code")).intValue(), "不存在的申诉应返回业务错误");

        // 收回申诉后订单回到进行中，不能再被裁决
        appealService.withdraw(appeal.getId(), seller.getId());
        ResponseEntity<Map> withdrawn = rest.postForEntity(
                url("/api/admin/appeals/" + appeal.getId() + "/arbitrate"),
                new HttpEntity<>(Map.of("outcome", "RELEASE_TO_SELLER", "comment", "已撤回仍裁决"),
                        authHeaders(admin())), Map.class);
        assertEquals(40000, ((Number) withdrawn.getBody().get("code")).intValue(),
                "申诉已撤回，不得再裁决");
    }

    // ---------- 造数与断言辅助 ----------

    private long submitReport(User reporter, String targetType, Long targetId, String reason) {
        ResponseEntity<Map> response = rest.postForEntity(url("/api/reports"),
                new HttpEntity<>(Map.of("targetType", targetType, "targetId", targetId, "reason", reason),
                        authHeaders(reporter)), Map.class);
        assertEquals(0, ((Number) response.getBody().get("code")).intValue(),
                "学生提交举报应成功：" + response.getBody().get("message"));
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        if (data != null && data.get("id") != null) {
            return ((Number) data.get("id")).longValue();
        }
        // 接口未回传 id 时，从 PENDING 列表里按对象反查
        Map<String, Object> page = (Map<String, Object>) data(adminGet("/api/admin/reports/query?status=PENDING"));
        List<Map<String, Object>> items = (List<Map<String, Object>>) page.get("items");
        return items.stream()
                .filter(r -> targetType.equals(r.get("targetType"))
                        && ((Number) r.get("targetId")).longValue() == targetId)
                .mapToLong(r -> ((Number) r.get("id")).longValue())
                .findFirst().orElseThrow();
    }

    private void assertAudit(String action, String targetType, Long targetId, int expected, String message) {
        Map<String, Object> page = (Map<String, Object>) data(adminGet("/api/admin/audit?action=" + action));
        List<Map<String, Object>> rows = (List<Map<String, Object>>) page.get("items");
        long count = rows.stream()
                .filter(r -> targetType.equals(r.get("targetType"))
                        && targetId.equals(((Number) r.get("targetId")).longValue()))
                .count();
        assertEquals(expected, count, message);
    }

    private void assertResolvedNotification(User user, Long orderId) {
        List<Notification> notes = notificationService.list(user.getId()).stream()
                .filter(n -> "APPEAL_RESOLVED".equals(n.getType()) && orderId.equals(n.getRefId()))
                .toList();
        assertEquals(1, notes.size(), "买卖双方各应收到一条 APPEAL_RESOLVED 通知");
        assertFalse(notes.get(0).getContent().isBlank(), "通知内容不能为空");
    }

    private BigDecimal differenceOf() {
        Map<String, Object> funds = (Map<String, Object>) data(adminGet("/api/admin/funds"));
        return new BigDecimal(String.valueOf(funds.get("difference")));
    }

    private TradePost publishBounty(User publisher, String price) {
        PublishReq request = new PublishReq();
        request.setTitle("写操作验收悬赏 " + price);
        request.setDescription("管理端写操作验收用");
        request.setPrice(new BigDecimal(price));
        request.setCategory("学习");
        request.setDeadline(java.time.LocalDateTime.now().plusDays(7));
        return tradePostService.publish(publisher.getId(), request);
    }

    private TradePost publishService(User publisher, String price) {
        ServicePublishReq request = new ServicePublishReq();
        request.setTitle("写操作验收服务 " + price);
        request.setDescription("管理端写操作验收用服务");
        request.setPrice(new BigDecimal(price));
        request.setCategory("学习");
        return tradePostService.publishService(publisher.getId(), request);
    }

    private void recharge(User user, String amount) {
        walletService.recharge(user.getId(), new BigDecimal(amount));
    }

    private BigDecimal balanceOf(User user) {
        return walletRepository.findByUserId(user.getId()).orElseThrow().getBalance();
    }

    private BigDecimal frozenOf(User user) {
        return walletRepository.findByUserId(user.getId()).orElseThrow().getFrozen();
    }

    private ResponseEntity<Map> adminGet(String path) {
        return rest.exchange(url(path), HttpMethod.GET, new HttpEntity<>(authHeaders(admin())), Map.class);
    }

    private <T> T data(ResponseEntity<Map> response) {
        assertEquals(HttpStatus.OK, response.getStatusCode(), "HTTP 状态应为 200");
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(0, ((Number) body.get("code")).intValue(), "业务码应为 0：" + body.get("message"));
        return (T) body.get("data");
    }

    private User admin() {
        return userRepository.findByUsername("admin").orElseThrow();
    }

    private User register(String prefix) {
        return userService.register(prefix + seq.incrementAndGet(), "123456", "123456", "验收用户");
    }

    private HttpHeaders authHeaders(User user) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtUtil.generate(user.getId(), user.getUsername()));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
