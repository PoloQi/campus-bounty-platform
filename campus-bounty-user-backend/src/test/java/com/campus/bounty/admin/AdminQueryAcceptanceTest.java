package com.campus.bounty.admin;

import com.campus.bounty.config.JwtUtil;
import com.campus.bounty.trade.AppealService;
import com.campus.bounty.trade.OrderStatus;
import com.campus.bounty.trade.TradeOrder;
import com.campus.bounty.trade.TradeOrderRepository;
import com.campus.bounty.trade.TradeOrderService;
import com.campus.bounty.trade.TradePost;
import com.campus.bounty.trade.TradePostService;
import com.campus.bounty.trade.dto.PublishReq;
import com.campus.bounty.user.User;
import com.campus.bounty.user.UserRepository;
import com.campus.bounty.user.UserService;
import com.campus.bounty.wallet.WalletRepository;
import com.campus.bounty.wallet.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 管理端只读板块验收（看板 / 订单 / 用户 / 举报查询 / 资金 / 审计）。
 * <p>
 * 断言原则：不赌绝对数字（H2 里已有其他测试类的数据），只赌**内部一致性**——
 * 分布求和等于总数、待办数等于列表长度、看板冻结额等于钱包表真实 SUM。
 * 这样即便将来数据变化，测试仍然有意义；而一旦口径写歪，立刻红。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@SuppressWarnings("unchecked")
class AdminQueryAcceptanceTest {
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
    AppealService appealService;
    @Autowired
    TradeOrderRepository tradeOrderRepository;
    @Autowired
    WalletService walletService;
    @Autowired
    WalletRepository walletRepository;
    @Autowired
    UserService userService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    JwtUtil jwtUtil;
    @Autowired
    ReportRepository reportRepository;
    @Autowired
    JdbcClient jdbc;

    private final AtomicInteger seq = new AtomicInteger();

    @Test
    void dashboard_metrics_are_internally_consistent() {
        Map<String, Object> data = data(adminGet("/api/admin/dashboard"));

        Map<String, Object> totals = (Map<String, Object>) data.get("totals");
        assertNotNull(totals, "看板必须返回 totals");
        for (String key : List.of("students", "orders", "gmv", "frozen", "openAppeals", "pendingReports")) {
            assertTrue(totals.containsKey(key), "看板 totals 缺字段 " + key);
        }

        List<Map<String, Object>> distribution = (List<Map<String, Object>>) data.get("distribution");
        assertEquals(OrderStatus.values().length, distribution.size(), "状态分布必须覆盖全部订单状态，缺的补 0");
        long sum = distribution.stream().mapToLong(row -> ((Number) row.get("total")).longValue()).sum();
        assertEquals(((Number) totals.get("orders")).longValue(), sum,
                "状态分布求和必须等于订单总数，否则饼图和总数会自相矛盾");

        List<Map<String, Object>> trend = (List<Map<String, Object>>) data.get("trend");
        assertEquals(7, trend.size(), "趋势固定 7 天，缺数据的日子要补 0 而不是缺项");
        for (int i = 1; i < trend.size(); i++) {
            LocalDate prev = LocalDate.parse(String.valueOf(trend.get(i - 1).get("orderDay")));
            LocalDate curr = LocalDate.parse(String.valueOf(trend.get(i).get("orderDay")));
            assertTrue(prev.isBefore(curr), "趋势日期必须严格升序，不能重复或倒序");
        }
        assertEquals(LocalDate.now().toString(), String.valueOf(trend.get(6).get("orderDay")),
                "趋势最后一天必须是今天");

        // 待办数不是独立算出来的一个数字，它必须和两个列表接口对得上
        List<Map<String, Object>> pending = (List<Map<String, Object>>) data(adminGet("/api/admin/reports"));
        assertEquals(pending.size(), ((Number) totals.get("pendingReports")).longValue(),
                "PENDING 举报数必须等于 /api/admin/reports 返回条数");

        Map<String, Object> openPage = (Map<String, Object>) data(adminGet("/api/admin/appeals?status=OPEN"));
        List<Map<String, Object>> openItems = (List<Map<String, Object>>) openPage.get("items");
        if (openItems.size() < 20) {
            assertEquals(openItems.size(), ((Number) totals.get("openAppeals")).longValue(),
                    "OPEN 申诉数必须等于申诉列表（前提：OPEN 数量未超过单页条数）");
        }
    }

    @Test
    void orders_page_filters_and_detail_aggregate_evidence() {
        User buyer = register("qo_buyer");
        User seller = register("qo_seller");
        recharge(buyer, "100");
        TradePost post = publishBounty(buyer, "30.00");
        TradeOrder order = tradeOrderRepository.findByPostId(post.getId()).orElseThrow();
        tradeOrderService.accept(order.getId(), seller.getId());

        Map<String, Object> page = (Map<String, Object>) data(adminGet("/api/admin/orders?buyerId=" + buyer.getId()));
        assertTrue(page.containsKey("items") && page.containsKey("page") && page.containsKey("hasNext"),
                "列表必须返回分页结构 items/page/hasNext，前端分页控件依赖它");
        List<Map<String, Object>> items = (List<Map<String, Object>>) page.get("items");
        assertEquals(1, items.size(), "按买家过滤应只命中该买家的一单");

        Map<String, Object> row = items.get(0);
        assertEquals(order.getId().longValue(), ((Number) row.get("id")).longValue());
        assertEquals("BOUNTY", row.get("type"), "列表要带出帖子类型，前端类型列依赖它");
        assertEquals("IN_PROGRESS", row.get("status"));
        assertNull(row.get("openAppealId"));
        assertEquals(0, new BigDecimal("30.00").compareTo(new BigDecimal(String.valueOf(row.get("amount")))));

        Map<String, Object> none = (Map<String, Object>) data(
                adminGet("/api/admin/orders?buyerId=" + buyer.getId() + "&status=COMPLETED"));
        assertTrue(((List<?>) none.get("items")).isEmpty(), "该买家没有已完成订单，状态筛选应生效");

        // 订单进入纠纷后，详情里必须能看到这笔申诉，仲裁台靠它一屏取证
        var appeal = appealService.open(order.getId(), seller.getId(), "详情聚合验收用");
        Map<String, Object> disputedPage = data(
                adminGet("/api/admin/orders?buyerId=" + buyer.getId() + "&status=DISPUTED"));
        List<Map<String, Object>> disputedItems = (List<Map<String, Object>>) disputedPage.get("items");
        assertEquals(1, disputedItems.size());
        assertEquals(appeal.getId().longValue(), ((Number) disputedItems.get(0).get("openAppealId")).longValue());
        Map<String, Object> detail = (Map<String, Object>) data(adminGet("/api/admin/orders/" + order.getId()));
        for (String key : List.of("order", "post", "buyer", "seller", "logs", "appeals", "timeline")) {
            assertTrue(detail.containsKey(key), "详情必须聚合 " + key + "，仲裁台靠它一屏取证");
        }
        Map<String, Object> buyerView = (Map<String, Object>) detail.get("buyer");
        assertFalse(buyerView.containsKey("passwordHash"), "用户摘要绝不能带出 passwordHash");
        assertFalse(buyerView.containsKey("password"), "用户摘要绝不能带出 password");
        assertFalse(((List<?>) detail.get("appeals")).isEmpty(), "该单已有申诉记录，详情应带出");
    }

    @ParameterizedTest
    @CsvSource({
            "POST, DELETE_POST, DELETE_POST",
            "TRADE_POST, CLOSE_TRADE_POST, CLOSE_TRADE_POST",
            "POST, BAN_USER, BAN_USER",
            "TRADE_POST, BAN_USER, BAN_USER",
            "USER, BAN_USER, BAN_USER",
            "TRADE_POST, CLOSE-TRADE-POST, "
    })
    void migrated_audit_detail_supplies_historical_moderation_action(String targetType, String auditAction,
                                                                    String expectedAction) {
        long adminId = admin().getId();
        var report = new Report();
        report.setTargetType(targetType);
        report.setTargetId(410L);
        report.setReporterId(adminId);
        report.setReason("历史举报");
        report.setStatus("HANDLED");
        report.setResult("VALID");
        report.setHandlerId(adminId);
        report.setHandledAt(LocalDateTime.now());
        report.setHandleComment("旧管理端保留的处理意见");
        long reportId = reportRepository.save(report).getId();
        String detail = "处理举报#" + reportId + "（" + targetType + ":410）action=" + auditAction
                + " result=VALID；历史治理记录";
        jdbc.sql("""
                insert into t_admin_log (admin_id, action, target_type, target_id, detail, created_at)
                values (:admin, 'HANDLE_REPORT', 'REPORT', :report, :detail, :created)
                """).param("admin", adminId).param("report", reportId).param("detail", detail)
                .param("created", report.getHandledAt()).update();

        Map<String, Object> result = data(adminGet("/api/admin/reports/" + reportId));
        assertEquals("VALID", result.get("result"));
        assertEquals(report.getHandleComment(), result.get("comment"));
        assertEquals(expectedAction, result.get("moderationAction"));
        Map<String, Object> page = data(adminGet("/api/admin/reports/query?status=HANDLED&targetType=" + targetType));
        List<Map<String, Object>> items = (List<Map<String, Object>>) page.get("items");
        Map<String, Object> row = items.stream()
                .filter(item -> ((Number) item.get("id")).longValue() == reportId)
                .findFirst().orElseThrow();
        assertEquals(expectedAction, row.get("moderationAction"));
    }

    @Test
    void users_keyword_and_status_filters() {
        User target = register("qu_target");
        adminService.banUser(admin().getId(), target.getId());

        Map<String, Object> byKeyword = (Map<String, Object>) data(
                adminGet("/api/admin/users?keyword=" + target.getUsername()));
        List<Map<String, Object>> rows = (List<Map<String, Object>>) byKeyword.get("items");
        assertTrue(rows.stream().anyMatch(r -> ((Number) r.get("id")).longValue() == target.getId()),
                "按用户名关键字应能搜到目标用户");
        assertFalse(((Map<String, Object>) byKeyword).get("items").toString().contains("passwordHash"),
                "用户列表不得泄漏密码哈希");

        Map<String, Object> banned = (Map<String, Object>) data(adminGet("/api/admin/users?status=BANNED"));
        List<Map<String, Object>> bannedRows = (List<Map<String, Object>>) banned.get("items");
        assertTrue(bannedRows.stream().allMatch(r -> "BANNED".equals(r.get("status"))),
                "status=BANNED 只能返回被禁用户");
        assertTrue(bannedRows.stream().anyMatch(r -> ((Number) r.get("id")).longValue() == target.getId()));
    }

    @Test
    void reports_query_and_detail_expose_resolution_fields() {
        User reporter = register("qr_reporter");
        User offender = register("qr_offender");

        ResponseEntity<Map> submit = rest.postForEntity(url("/api/reports"),
                new HttpEntity<>(Map.of("targetType", "USER", "targetId", offender.getId(),
                        "reason", "言语骚扰，要求核查"), authHeaders(reporter)), Map.class);
        assertEquals(0, ((Number) submit.getBody().get("code")).intValue(), "学生提交举报应成功");

        Map<String, Object> page = (Map<String, Object>) data(adminGet("/api/admin/reports/query?status=PENDING"));
        List<Map<String, Object>> items = (List<Map<String, Object>>) page.get("items");
        Map<String, Object> mine = items.stream()
                .filter(r -> ((Number) r.get("targetId")).longValue() == offender.getId())
                .findFirst().orElse(null);
        assertNotNull(mine, "刚提交的举报应出现在 PENDING 列表里");
        assertEquals("USER", mine.get("targetType"));
        assertEquals("PENDING", mine.get("status"));
        assertEquals(reporter.getId().longValue(), ((Number) mine.get("reporterId")).longValue());

        Map<String, Object> detail = (Map<String, Object>) data(
                adminGet("/api/admin/reports/" + ((Number) mine.get("id")).longValue()));
        for (String key : List.of("result", "comment", "adminId", "handledAt", "moderationAction")) {
            assertTrue(detail.containsKey(key), "举报详情必须暴露处置字段 " + key + "（前端据它渲染处理结果）");
        }
        assertEquals("PENDING", detail.get("status"));

        Map<String, Object> moderation = (Map<String, Object>) data(
                adminGet("/api/admin/moderation/USER/" + offender.getId()));
        assertEquals(offender.getId().longValue(), ((Number) moderation.get("id")).longValue(),
                "处置前预览接口应能取到被举报对象现状");
        assertNotNull(moderation.get("status"));
    }

    @Test
    void funds_reconcile_with_wallet_table() {
        Map<String, Object> funds = (Map<String, Object>) data(adminGet("/api/admin/funds"));
        BigDecimal frozen = new BigDecimal(String.valueOf(funds.get("frozen")));
        BigDecimal expected = new BigDecimal(String.valueOf(funds.get("expected")));
        BigDecimal difference = new BigDecimal(String.valueOf(funds.get("difference")));

        assertEquals(0, frozen.compareTo(walletRepository.totalFrozen()),
                "看板冻结额必须等于钱包表 SUM(frozen)，否则两个页面对不上账");
        assertEquals(0, frozen.subtract(expected).compareTo(difference),
                "difference 的定义必须是 frozen - expected（托管冻结与在途应冻的差）");
    }

    @Test
    void ledger_and_audit_are_filterable() {
        User user = register("ql_user");
        recharge(user, "50");

        Map<String, Object> ledger = (Map<String, Object>) data(
                adminGet("/api/admin/funds/ledger?userId=" + user.getId()));
        List<Map<String, Object>> logs = (List<Map<String, Object>>) ledger.get("items");
        assertFalse(logs.isEmpty(), "用户有充值流水，账本不该为空");
        assertTrue(logs.stream().allMatch(r -> ((Number) r.get("userId")).longValue() == user.getId()),
                "按用户过滤的账本不能混入别人的流水");

        User victim = register("ql_victim");
        adminService.banUser(admin().getId(), victim.getId());
        Map<String, Object> audit = (Map<String, Object>) data(adminGet("/api/admin/audit?action=BAN_USER"));
        List<Map<String, Object>> rows = (List<Map<String, Object>>) audit.get("items");
        assertFalse(rows.isEmpty(), "刚封禁过一个用户，审计不该为空");
        assertTrue(rows.stream().allMatch(r -> "BAN_USER".equals(r.get("action"))),
                "action 过滤必须生效，否则审计页会混入全部动作");
        assertTrue(rows.stream().anyMatch(r -> ((Number) r.get("targetId")).longValue() == victim.getId()),
                "审计应记录到本次封禁的目标用户");
    }

    @Test
    void student_forbidden_and_anonymous_unauthorized() {
        User student = register("qp_student");
        for (String path : List.of("/api/admin/dashboard", "/api/admin/orders", "/api/admin/users",
                "/api/admin/reports", "/api/admin/reports/query", "/api/admin/funds",
                "/api/admin/funds/ledger", "/api/admin/audit", "/api/admin/appeals")) {
            ResponseEntity<Map> response = rest.exchange(url(path), HttpMethod.GET,
                    new HttpEntity<>(authHeaders(student)), Map.class);
            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(), path + " 必须拒绝学生 token");
        }
        ResponseEntity<Map> anonymous = rest.exchange(url("/api/admin/dashboard"), HttpMethod.GET,
                HttpEntity.EMPTY, Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, anonymous.getStatusCode(), "未登录访问管理端必须 401");
    }

    // ---------- 造数与断言辅助 ----------

    private TradePost publishBounty(User publisher, String price) {
        PublishReq request = new PublishReq();
        request.setTitle("只读验收悬赏 " + price);
        request.setDescription("管理端只读板块验收用");
        request.setPrice(new BigDecimal(price));
        request.setCategory("学习");
        request.setDeadline(java.time.LocalDateTime.now().plusDays(7));
        return tradePostService.publish(publisher.getId(), request);
    }

    private void recharge(User user, String amount) {
        walletService.recharge(user.getId(), new BigDecimal(amount));
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
