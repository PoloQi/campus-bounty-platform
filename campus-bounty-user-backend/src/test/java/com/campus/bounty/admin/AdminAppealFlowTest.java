package com.campus.bounty.admin;

import com.campus.bounty.admin.dto.AppealDetailView;
import com.campus.bounty.admin.dto.AppealListItem;
import com.campus.bounty.common.BizException;
import com.campus.bounty.config.JwtUtil;
import com.campus.bounty.notification.Notification;
import com.campus.bounty.notification.NotificationService;
import com.campus.bounty.trade.Appeal;
import com.campus.bounty.trade.AppealService;
import com.campus.bounty.trade.OrderStatus;
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
import com.campus.bounty.wallet.WalletLog;
import com.campus.bounty.wallet.WalletLogRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B6 申诉仲裁验收。
 * <p>
 * 本板块是整个管理端唯一动钱的入口，所以断言的重点不是「接口返回 200」，
 * 而是四件资金事实：钱只动一次、动的方向和金额正确、冻结差额守恒、动完留下唯一一条审计。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@SuppressWarnings("unchecked")
class AdminAppealFlowTest {
    @LocalServerPort
    int port;
    @Autowired
    TestRestTemplate rest;
    @Autowired
    AdminAppealService adminAppealService;
    @Autowired
    AdminQueryService adminQueryService;
    @Autowired
    AuditLogService auditLogService;
    @Autowired
    NotificationService notificationService;
    @Autowired
    AppealService appealService;
    @Autowired
    TradePostService tradePostService;
    @Autowired
    TradeOrderService tradeOrderService;
    @Autowired
    TradePostRepository tradePostRepository;
    @Autowired
    TradeOrderRepository tradeOrderRepository;
    @Autowired
    WalletService walletService;
    @Autowired
    WalletRepository walletRepository;
    @Autowired
    WalletLogRepository walletLogRepository;
    @Autowired
    UserService userService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    JwtUtil jwtUtil;

    @Test
    void release_pays_seller_and_completes_bounty_order() {
        User buyer = register("apl_rel_buyer");
        User seller = register("apl_rel_seller");
        recharge(buyer, "100");
        TradePost post = publishBounty(buyer, "20.00");
        TradeOrder order = orderOf(post);
        tradeOrderService.accept(order.getId(), seller.getId());
        Appeal appeal = openAppeal(order, seller, "已完成跑腿，对方反悔");

        BigDecimal diffBefore = diffOfFrozenSummary();

        Appeal resolved = adminAppealService.release(admin().getId(), appeal.getId(), "核实已按时送达，判付");

        assertEquals("RESOLVED", resolved.getStatus());
        assertEquals(AdminAppealService.RELEASE_TO_SELLER, resolved.getOutcome());
        assertEquals(admin().getId(), resolved.getAdminId());
        assertEquals("核实已按时送达，判付", resolved.getAdminComment());
        assertNotNull(resolved.getHandledAt());

        TradeOrder settled = tradeOrderRepository.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.COMPLETED, settled.getStatus());
        assertEquals(PostStatus.CLOSED, tradePostRepository.findById(post.getId()).orElseThrow().getStatus(),
                "悬赏帖裁决后应关闭");

        assertAmount("80.00", balanceOf(buyer), "买家冻结的 20 元应被划走，余额不再变化");
        assertAmount("0.00", frozenOf(buyer), "买家冻结应清零");
        assertAmount("20.00", balanceOf(seller), "卖家应收到全额赏金");

        List<AdminLog> audits = audits(AuditAction.APPEAL_RELEASE, appeal.getId());
        assertEquals(1, audits.size(), "一次裁决只应留一条 APPEAL_RELEASE 审计");
        assertEquals("APPEAL", audits.get(0).getTargetType());
        assertTrue(audits.get(0).getDetail().contains("20"), audits.get(0).getDetail());
        assertTrue(audits.get(0).getDetail().contains("判付"), audits.get(0).getDetail());

        assertResolvedNotification(buyer, order.getId(), "判付");
        assertResolvedNotification(seller, order.getId(), "判付");

        List<WalletLog> logs = walletLogRepository.findByOrderIdOrderByIdAsc(order.getId());
        assertTrue(logs.stream().anyMatch(log -> "PAY".equals(log.getType())), "应有买家支出流水");
        assertTrue(logs.stream().anyMatch(log -> "INCOME".equals(log.getType())), "应有卖家收入流水");

        assertAmount("0.00", diffOfFrozenSummary().subtract(diffBefore),
                "裁决前后冻结总额与在途金额的差额应保持不变");
    }

    @Test
    void refund_returns_money_to_buyer_and_keeps_service_post_open() {
        User seller = register("apl_ref_seller");
        User buyer = register("apl_ref_buyer");
        recharge(buyer, "50");
        TradePost service = publishService(seller, "30.00");
        TradeOrder order = tradeOrderService.buy(service.getId(), buyer.getId());
        Appeal appeal = openAppeal(order, buyer, "对方未按约授课");

        adminAppealService.refund(admin().getId(), appeal.getId(), "核实未履约，同意退款");

        assertEquals(OrderStatus.CANCELLED,
                tradeOrderRepository.findById(order.getId()).orElseThrow().getStatus());
        assertEquals(PostStatus.OPEN,
                tradePostRepository.findById(service.getId()).orElseThrow().getStatus(),
                "服务帖是一帖多单，单笔纠纷不应下架整帖");

        assertAmount("50.00", balanceOf(buyer), "托管金应全额退回买家");
        assertAmount("0.00", frozenOf(buyer));
        assertAmount("0.00", balanceOf(seller), "卖家不应得到任何款项");

        assertEquals(1, audits(AuditAction.APPEAL_REFUND, appeal.getId()).size());
        assertResolvedNotification(buyer, order.getId(), "判退");
        assertResolvedNotification(seller, order.getId(), "判退");
    }

    @Test
    void resolved_appeal_cannot_be_resolved_again_and_money_moves_only_once() {
        User buyer = register("apl_twice_buyer");
        User seller = register("apl_twice_seller");
        recharge(buyer, "100");
        TradePost post = publishBounty(buyer, "15.00");
        TradeOrder order = orderOf(post);
        tradeOrderService.accept(order.getId(), seller.getId());
        Appeal appeal = openAppeal(order, seller, "重复裁决测试");

        adminAppealService.release(admin().getId(), appeal.getId(), "第一次裁决");
        int logCountAfterFirst = walletLogRepository.findByOrderIdOrderByIdAsc(order.getId()).size();

        BizException error = assertThrows(BizException.class,
                () -> adminAppealService.release(admin().getId(), appeal.getId(), "第二次裁决"));
        assertTrue(error.getMessage().contains("已处理"), error.getMessage());

        assertAmount("15.00", balanceOf(seller), "重复裁决不能二次打款");
        assertEquals(logCountAfterFirst,
                walletLogRepository.findByOrderIdOrderByIdAsc(order.getId()).size(),
                "重复裁决不能产生新流水");
        assertEquals(1, audits(AuditAction.APPEAL_RELEASE, appeal.getId()).size());
    }

    @Test
    void withdrawn_appeal_cannot_be_resolved() {
        User buyer = register("apl_wd_buyer");
        User seller = register("apl_wd_seller");
        recharge(buyer, "100");
        TradePost post = publishBounty(buyer, "12.00");
        TradeOrder order = orderOf(post);
        tradeOrderService.accept(order.getId(), seller.getId());
        Appeal appeal = openAppeal(order, seller, "撤回后不可裁决");

        appealService.withdraw(appeal.getId(), seller.getId());
        assertEquals(OrderStatus.IN_PROGRESS,
                tradeOrderRepository.findById(order.getId()).orElseThrow().getStatus());

        BizException error = assertThrows(BizException.class,
                () -> adminAppealService.refund(admin().getId(), appeal.getId(), "试试裁决"));
        assertTrue(error.getMessage().contains("已处理"), error.getMessage());
        assertAmount("0.00", balanceOf(seller));
    }

    @Test
    void blank_comment_is_rejected_before_money_moves() {
        User buyer = register("apl_cmt_buyer");
        User seller = register("apl_cmt_seller");
        recharge(buyer, "100");
        TradePost post = publishBounty(buyer, "9.00");
        TradeOrder order = orderOf(post);
        tradeOrderService.accept(order.getId(), seller.getId());
        Appeal appeal = openAppeal(order, seller, "空意见测试");

        BizException error = assertThrows(BizException.class,
                () -> adminAppealService.release(admin().getId(), appeal.getId(), "   "));
        assertTrue(error.getMessage().contains("处理意见"), error.getMessage());

        assertEquals("OPEN", appealService.viewOpen(order.getId(), seller.getId()).getStatus(),
                "校验失败不应改动申诉状态");
        assertAmount("0.00", balanceOf(seller), "校验失败不能动钱");
    }

    @Test
    void appeal_detail_exposes_both_sides_and_appellant_role() {
        User buyer = register("apl_det_buyer");
        User seller = register("apl_det_seller");
        recharge(buyer, "100");
        TradePost post = publishBounty(buyer, "18.00");
        TradeOrder order = orderOf(post);
        tradeOrderService.accept(order.getId(), seller.getId());
        Appeal appeal = openAppeal(order, seller, "详情字段测试");

        AppealDetailView view = adminAppealService.detail(admin().getId(), appeal.getId());

        assertEquals("SELLER", view.getAppellantRole(), "卖家申诉时角色应识别为 SELLER");
        assertEquals(seller.getId(), view.getAppellant().getId());
        assertEquals(buyer.getId(), view.getBuyer().getId());
        assertEquals(seller.getId(), view.getSeller().getId());
        assertEquals(OrderStatus.DISPUTED, view.getOrder().getStatus());
        assertEquals("详情字段测试", view.getAppeal().getReason());
        assertTrue(view.getWalletLogs().stream().anyMatch(log -> "FREEZE".equals(log.getType())),
                "仲裁台应能看到该单的冻结流水");
    }

    @Test
    void list_puts_open_appeals_first_and_filters_by_status() {
        User buyer = register("apl_lst_buyer");
        User seller = register("apl_lst_seller");
        recharge(buyer, "100");
        TradePost firstPost = publishBounty(buyer, "11.00");
        TradeOrder firstOrder = orderOf(firstPost);
        tradeOrderService.accept(firstOrder.getId(), seller.getId());
        Appeal resolvedOne = openAppeal(firstOrder, seller, "列表-将被裁决");
        adminAppealService.release(admin().getId(), resolvedOne.getId(), "结案");

        TradePost secondPost = publishBounty(buyer, "13.00");
        TradeOrder secondOrder = orderOf(secondPost);
        tradeOrderService.accept(secondOrder.getId(), seller.getId());
        Appeal openOne = openAppeal(secondOrder, seller, "列表-待处理");

        List<AppealListItem> all = adminAppealService.list(admin().getId(), null);
        assertTrue(!all.isEmpty() && "OPEN".equals(all.get(0).getStatus()),
                "OPEN 申诉必须排在最前面");
        assertTrue(all.stream().anyMatch(row -> resolvedOne.getId().equals(row.getId())
                && "RESOLVED".equals(row.getStatus())));

        List<AppealListItem> openOnly = adminAppealService.list(admin().getId(), "OPEN");
        assertTrue(openOnly.stream().allMatch(row -> "OPEN".equals(row.getStatus())));
        assertTrue(openOnly.stream().anyMatch(row -> openOne.getId().equals(row.getId())));
        assertTrue(openOnly.stream().noneMatch(row -> resolvedOne.getId().equals(row.getId())));

        // 列表行必须自带判断依据：谁申诉、多少钱、哪一单
        AppealListItem row = openOnly.stream()
                .filter(item -> openOne.getId().equals(item.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals(seller.getId(), row.getAppellantId());
        assertEquals("BOUNTY", row.getPostType());
        assertEquals(secondOrder.getId(), row.getOrderId());
        assertAmount("13.00", row.getOrderAmount());
        assertNotNull(row.getAppellantName());
    }

    @Test
    void student_forbidden_anonymous_unauthorized_and_unknown_appeal_returns_biz_error() {
        User student = register("apl_sec_student");

        ResponseEntity<Map> listDenied = rest.exchange(url("/api/admin/appeals"),
                HttpMethod.GET, new HttpEntity<>(authHeaders(student)), Map.class);
        assertEquals(HttpStatus.FORBIDDEN, listDenied.getStatusCode());

        ResponseEntity<Map> detailDenied = rest.exchange(url("/api/admin/appeals/1"),
                HttpMethod.GET, new HttpEntity<>(authHeaders(student)), Map.class);
        assertEquals(HttpStatus.FORBIDDEN, detailDenied.getStatusCode());

        ResponseEntity<Map> releaseDenied = rest.postForEntity(url("/api/admin/appeals/1/release"),
                new HttpEntity<>(Map.of("comment", "越权裁决"), authHeaders(student)), Map.class);
        assertEquals(HttpStatus.FORBIDDEN, releaseDenied.getStatusCode());

        ResponseEntity<Map> anonymous = rest.exchange(url("/api/admin/appeals"),
                HttpMethod.GET, HttpEntity.EMPTY, Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, anonymous.getStatusCode());

        ResponseEntity<Map> notFound = rest.postForEntity(url("/api/admin/appeals/99999999/refund"),
                new HttpEntity<>(Map.of("comment", "不存在"), authHeaders(admin())), Map.class);
        assertEquals(HttpStatus.OK, notFound.getStatusCode());
        assertEquals(40000, ((Number) notFound.getBody().get("code")).intValue());

        ResponseEntity<Map> blank = rest.postForEntity(url("/api/admin/appeals/99999999/release"),
                new HttpEntity<>(Map.of("comment", ""), authHeaders(admin())), Map.class);
        assertEquals(40000, ((Number) blank.getBody().get("code")).intValue(),
                "空处理意见应被 @NotBlank 拦下");
    }

    // ---------- 造数与断言辅助 ----------

    private TradePost publishBounty(User publisher, String price) {
        PublishReq request = new PublishReq();
        request.setTitle("仲裁测试悬赏 " + price);
        request.setDescription("B6 仲裁链路测试用");
        request.setPrice(new BigDecimal(price));
        request.setCategory("学习");
        return tradePostService.publish(publisher.getId(), request);
    }

    private TradePost publishService(User publisher, String price) {
        ServicePublishReq request = new ServicePublishReq();
        request.setTitle("仲裁测试服务 " + price);
        request.setDescription("B6 仲裁链路测试用服务");
        request.setPrice(new BigDecimal(price));
        request.setCategory("学习");
        return tradePostService.publishService(publisher.getId(), request);
    }

    private TradeOrder orderOf(TradePost post) {
        return tradeOrderRepository.findByPostId(post.getId()).orElseThrow();
    }

    private Appeal openAppeal(TradeOrder order, User appellant, String reason) {
        return appealService.open(order.getId(), appellant.getId(), reason);
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

    private BigDecimal diffOfFrozenSummary() {
        return adminQueryService.frozenSummary(admin().getId()).getDiff();
    }

    private List<AdminLog> audits(String action, Long appealId) {
        return auditLogService.query(null, action, null, null).stream()
                .filter(log -> appealId.equals(log.getTargetId()))
                .toList();
    }

    private void assertResolvedNotification(User user, Long orderId, String keyword) {
        List<Notification> notes = notificationService.list(user.getId()).stream()
                .filter(note -> "APPEAL_RESOLVED".equals(note.getType()) && orderId.equals(note.getRefId()))
                .toList();
        assertEquals(1, notes.size(), "双方各应收到一条 APPEAL_RESOLVED 通知");
        assertTrue(notes.get(0).getContent().contains(keyword), notes.get(0).getContent());
    }

    private void assertAmount(String expected, BigDecimal actual) {
        assertAmount(expected, actual, "");
    }

    private void assertAmount(String expected, BigDecimal actual, String message) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                message + "（期望 " + expected + "，实际 " + actual + "）");
    }

    private User admin() {
        return userRepository.findByUsername("admin").orElseThrow();
    }

    private User register(String username) {
        return userService.register(username, "123456", "123456", "仲裁测试用户");
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
