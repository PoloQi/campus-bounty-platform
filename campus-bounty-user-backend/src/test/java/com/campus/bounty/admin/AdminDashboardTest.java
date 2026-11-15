package com.campus.bounty.admin;

import com.campus.bounty.admin.dto.DashboardView;
import com.campus.bounty.admin.dto.HandleReportReq;
import com.campus.bounty.config.JwtUtil;
import com.campus.bounty.trade.Appeal;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B7 数据看板验收。
 * <p>
 * 看板没有写入、没有新的权限边界，风险全在「数算错了但页面看着正常」。所以断言分三层：
 * <ol>
 *   <li><b>内部自洽</b>：状态分布求和必须等于订单总数（这就是只读事务要解决的问题）</li>
 *   <li><b>增量对得上</b>：造一笔 20 元订单走完确认，各指标变化量要精确等于 1 / 1 / 20</li>
 *   <li><b>口径与既有接口一致</b>：待办数必须等于对应列表接口的条数</li>
 * </ol>
 * 一律用增量断言——H2 里已有其他测试类的存量数据，绝对值断言必然误报（台账坑 #9）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@SuppressWarnings("unchecked")
class AdminDashboardTest {
    @LocalServerPort
    int port;
    @Autowired
    TestRestTemplate rest;
    @Autowired
    AdminDashboardService dashboardService;
    @Autowired
    AdminService adminService;
    @Autowired
    AdminReportService adminReportService;
    @Autowired
    AdminAppealService adminAppealService;
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
    ReportRepository reportRepository;
    @Autowired
    UserService userService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    JwtUtil jwtUtil;

    @Test
    void distribution_matches_totals_and_covers_all_six_statuses() {
        DashboardView view = dashboard();

        List<DashboardView.StatusCount> distribution = view.getStatusDistribution();
        assertEquals(OrderStatus.values().length, distribution.size(),
                "6 个状态必须全部出现，缺的补 0 而不是省略");

        for (int i = 0; i < OrderStatus.values().length; i++) {
            assertEquals(OrderStatus.values()[i].name(), distribution.get(i).getStatus(),
                    "分布顺序应与 OrderStatus 声明顺序一致，饼图图例才不会随机跳动");
        }

        long sum = distribution.stream().mapToLong(DashboardView.StatusCount::getCount).sum();
        assertEquals(view.getOrderTotal(), sum,
                "分布求和必须等于订单总数：两者不在同一快照上时会差 1，这个断言就是为它写的");

        assertEquals(countOf(view, OrderStatus.COMPLETED), view.getOrderCompleted(),
                "orderCompleted 应与分布里的 COMPLETED 一致");

        assertEquals(userRepository.count(), view.getUserTotal(), "用户数口径是全表计数");
        assertEquals(tradeOrderRepository.count(), view.getOrderTotal(), "订单数口径是全表计数");
        assertNotNull(view.getGmv());
        assertNotNull(view.getFrozenTotal());
        assertTrue(view.getGmv().compareTo(BigDecimal.ZERO) >= 0, "GMV 不应为负");
        assertEquals(0, walletRepository.totalFrozen().compareTo(view.getFrozenTotal()),
                "冻结总额必须与 B3 冻结核对接口同一口径");
    }

    @Test
    void completing_an_order_moves_every_related_metric_by_exact_amount() {
        User buyer = register("dash_buyer");
        User seller = register("dash_seller");
        recharge(buyer, "100");
        DashboardView before = dashboard();
        long createdBefore = todayCreated(before);
        long completedBefore = todayCompleted(before);
        long completedStatusBefore = countOf(before, OrderStatus.COMPLETED);

        TradePost post = publishBounty(buyer, "20.00");
        TradeOrder order = tradeOrderRepository.findByPostId(post.getId()).orElseThrow();

        // 冻结基准必须取在发布之后：发布冻结 +20、结算释放 -20，从发布前开始算净值恒为 0，
        // 那样这条断言就没有检验力了。这里只考察「结算这一步释放了多少」。
        BigDecimal frozenAfterPublish = dashboard().getFrozenTotal();

        tradeOrderService.accept(order.getId(), seller.getId());
        tradeOrderService.deliver(order.getId(), seller.getId());
        tradeOrderService.confirm(order.getId(), buyer.getId());

        DashboardView after = dashboard();

        assertEquals(before.getOrderTotal() + 1, after.getOrderTotal(), "订单总数应 +1");
        assertEquals(before.getOrderCompleted() + 1, after.getOrderCompleted(), "完成数应 +1");
        assertEquals(completedStatusBefore + 1, countOf(after, OrderStatus.COMPLETED));
        assertEquals(0, before.getGmv().add(new BigDecimal("20.00")).compareTo(after.getGmv()),
                "GMV 应精确增加 20（期望 " + before.getGmv().add(new BigDecimal("20.00"))
                        + "，实际 " + after.getGmv() + "）");
        assertEquals(createdBefore + 1, todayCreated(after), "今天的「新建订单」应 +1");
        assertEquals(completedBefore + 1, todayCompleted(after), "今天的「完成订单」应 +1");

        // 结算释放托管金：看板与冻结核对页不能各说各话
        assertEquals(0, frozenAfterPublish.subtract(new BigDecimal("20.00"))
                .compareTo(after.getFrozenTotal()),
                "结算后平台冻结总额应下降 20（结算前 " + frozenAfterPublish
                        + "，结算后 " + after.getFrozenTotal() + "）");
    }

    @Test
    void trend_is_seven_consecutive_days_ending_today_with_zeros_filled() {
        DashboardView view = dashboard();
        List<DashboardView.TrendPoint> trend = view.getTrend();

        assertEquals(7, trend.size(), "趋势固定 7 项");
        LocalDate today = LocalDate.now();
        for (int i = 0; i < trend.size(); i++) {
            assertEquals(today.minusDays(6 - i), trend.get(i).getDate(),
                    "日期必须连续升序，最后一项是今天");
        }

        // 6 天前不可能有本批次造的数据：这一项存在且为 0，证明是「补 0」而非「缺项」
        DashboardView.TrendPoint oldest = trend.get(0);
        assertEquals(0, oldest.getCreated(), "无数据的历史日期应为 0，而不是被省略");
        assertEquals(0, oldest.getCompleted());
    }

    @Test
    void todo_counts_equal_the_corresponding_list_endpoints() {
        User buyer = register("dash_todo_buyer");
        User seller = register("dash_todo_seller");
        recharge(buyer, "100");

        DashboardView baseline = dashboard();
        long pendingBefore = baseline.getPendingReportCount();
        long openBefore = baseline.getOpenAppealCount();

        adminService.report(buyer.getId(), "USER", seller.getId(), "看板待办测试举报");

        TradePost post = publishBounty(buyer, "10.00");
        TradeOrder order = tradeOrderRepository.findByPostId(post.getId()).orElseThrow();
        tradeOrderService.accept(order.getId(), seller.getId());
        Appeal appeal = appealService.open(order.getId(), seller.getId(), "看板待办测试申诉");

        DashboardView view = dashboard();
        assertEquals(pendingBefore + 1, view.getPendingReportCount(), "PENDING 举报待办应 +1");
        assertEquals(openBefore + 1, view.getOpenAppealCount(), "OPEN 申诉待办应 +1");

        // 与列表接口对齐：数字对不上说明两处口径不同
        assertEquals(adminReportService.list(admin().getId(), "PENDING", null).size(),
                view.getPendingReportCount(),
                "待办举报数必须等于 /api/admin/reports?status=PENDING 的条数");
        assertEquals(adminAppealService.list(admin().getId(), "OPEN").size(),
                view.getOpenAppealCount(),
                "待办申诉数必须等于 /api/admin/appeals?status=OPEN 的条数");

        // 处置掉这条举报，待办立刻回落 —— 证明它是实时 COUNT，不是缓存
        Report report = reportRepository.findByStatusOrderByIdDesc("PENDING").stream()
                .filter(row -> "看板待办测试举报".equals(row.getReason()))
                .findFirst().orElseThrow();
        HandleReportReq reject = new HandleReportReq();
        reject.setAction(AdminReportService.ACTION_REJECT);
        reject.setComment("看板测试：驳回");
        adminReportService.handle(admin().getId(), report.getId(), reject);
        assertEquals(pendingBefore, dashboard().getPendingReportCount(),
                "举报处置后待办数应回到原值");

        // 裁决掉这条申诉，同理
        adminAppealService.refund(admin().getId(), appeal.getId(), "看板测试：判退");
        assertEquals(openBefore, dashboard().getOpenAppealCount(),
                "申诉裁决后待办数应回到原值");
    }

    @Test
    void dashboard_requires_admin_role() {
        User student = register("dash_student");

        ResponseEntity<Map> anonymous = rest.exchange(url("/api/admin/dashboard"),
                HttpMethod.GET, HttpEntity.EMPTY, Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, anonymous.getStatusCode(), "未登录应 401");

        ResponseEntity<Map> forbidden = rest.exchange(url("/api/admin/dashboard"),
                HttpMethod.GET, new HttpEntity<>(authHeaders(student)), Map.class);
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode(), "学生应 403");

        ResponseEntity<Map> ok = rest.exchange(url("/api/admin/dashboard"),
                HttpMethod.GET, new HttpEntity<>(authHeaders(admin())), Map.class);
        assertEquals(HttpStatus.OK, ok.getStatusCode());
        assertEquals(0, ((Number) ok.getBody().get("code")).intValue());

        Map<String, Object> data = (Map<String, Object>) ok.getBody().get("data");
        for (String key : List.of("userTotal", "orderTotal", "orderCompleted", "gmv", "frozenTotal",
                "pendingReportCount", "openAppealCount", "statusDistribution", "trend")) {
            assertTrue(data.containsKey(key), "响应缺少字段：" + key);
        }
        assertEquals(7, ((List<?>) data.get("trend")).size());
        assertEquals(6, ((List<?>) data.get("statusDistribution")).size());
    }

    // ---------- 造数与断言辅助 ----------

    private DashboardView dashboard() {
        return dashboardService.overview(admin().getId());
    }

    private long countOf(DashboardView view, OrderStatus status) {
        return view.getStatusDistribution().stream()
                .filter(row -> status.name().equals(row.getStatus()))
                .findFirst().orElseThrow().getCount();
    }

    private long todayCreated(DashboardView view) {
        return view.getTrend().stream()
                .filter(point -> LocalDate.now().equals(point.getDate()))
                .findFirst().orElseThrow().getCreated();
    }

    private long todayCompleted(DashboardView view) {
        return view.getTrend().stream()
                .filter(point -> LocalDate.now().equals(point.getDate()))
                .findFirst().orElseThrow().getCompleted();
    }

    private TradePost publishBounty(User publisher, String price) {
        PublishReq request = new PublishReq();
        request.setTitle("看板测试悬赏 " + price);
        request.setDescription("B7 看板链路测试用");
        request.setPrice(new BigDecimal(price));
        request.setCategory("学习");
        return tradePostService.publish(publisher.getId(), request);
    }

    private void recharge(User user, String amount) {
        walletService.recharge(user.getId(), new BigDecimal(amount));
    }

    private User admin() {
        return userRepository.findByUsername("admin").orElseThrow();
    }

    private User register(String username) {
        return userService.register(username, "123456", "123456", "看板测试用户");
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
