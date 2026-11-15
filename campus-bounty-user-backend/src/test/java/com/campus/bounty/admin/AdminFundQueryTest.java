package com.campus.bounty.admin;

import com.campus.bounty.config.JwtUtil;
import com.campus.bounty.trade.TradeOrder;
import com.campus.bounty.trade.TradeOrderRepository;
import com.campus.bounty.trade.TradeOrderService;
import com.campus.bounty.trade.TradePost;
import com.campus.bounty.trade.TradePostService;
import com.campus.bounty.trade.dto.PublishReq;
import com.campus.bounty.user.User;
import com.campus.bounty.user.UserRepository;
import com.campus.bounty.user.UserService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B3 用户与资金只读板块验收。
 * <p>
 * 关于冻结差额：H2 库跨测试类保留数据，而 {@code WalletDomainTest} 里有
 * {@code walletService.freeze(user, amount, 101L)} 这类「用合成订单号直接冻结」的用例，
 * 库里并不存在这些订单。因此全局 diff 天生不为 0，断言只能落在
 * **增量守恒**上：执行一条资金路径前后，diff 的变化量必须为 0。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@SuppressWarnings("unchecked")
class AdminFundQueryTest {
    @LocalServerPort
    int port;
    @Autowired
    TestRestTemplate rest;
    @Autowired
    AdminService adminService;
    @Autowired
    UserService userService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    WalletService walletService;
    @Autowired
    TradePostService tradePostService;
    @Autowired
    TradeOrderService tradeOrderService;
    @Autowired
    TradeOrderRepository orderRepository;
    @Autowired
    JwtUtil jwtUtil;

    @Test
    void admin_filters_users_by_keyword_role_and_status() {
        register("fund_ua", "AlphaUser");
        User beta = register("fund_ub", "BetaUser");

        List<Map<String, Object>> byUsernameKeyword = listUsers("keyword=fund_ua");
        assertTrue(usernamesOf(byUsernameKeyword).contains("fund_ua"), "用户名模糊匹配应命中");
        assertFalse(usernamesOf(byUsernameKeyword).contains("fund_ub"), "不应命中另一个用户");

        List<Map<String, Object>> byNicknameKeyword = listUsers("keyword=BetaUser");
        assertTrue(usernamesOf(byNicknameKeyword).contains("fund_ub"), "昵称模糊匹配应命中");

        List<Map<String, Object>> admins = listUsers("role=ADMIN");
        assertFalse(admins.isEmpty(), "应至少有一个管理员");
        assertTrue(admins.stream().allMatch(row -> "ADMIN".equals(row.get("role"))),
                "role=ADMIN 的结果必须全是管理员");

        adminService.banUser(admin().getId(), beta.getId());
        List<Map<String, Object>> banned = listUsers("status=BANNED");
        assertTrue(usernamesOf(banned).contains("fund_ub"), "封禁后应按 status=BANNED 查到");
        assertTrue(banned.stream().allMatch(row -> "BANNED".equals(row.get("status"))));

        String past = LocalDateTime.now().minusHours(1).withNano(0)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        assertTrue(usernamesOf(listUsers("from=" + past)).contains("fund_ua"), "from 在过去应包含刚注册用户");
        assertFalse(usernamesOf(listUsers("to=" + past)).contains("fund_ua"), "to 在过去不应包含刚注册用户");
    }

    @Test
    void admin_reads_single_user_wallet_and_logs() {
        User user = register("fund_wallet", "钱包用户");
        walletService.recharge(user.getId(), new BigDecimal("100.00"));

        Map<String, Object> wallet = dataOf("/api/admin/users/" + user.getId() + "/wallet");
        assertEquals(100.0, ((Number) wallet.get("balance")).doubleValue(), 0.001);
        assertEquals(0.0, ((Number) wallet.get("frozen")).doubleValue(), 0.001);
        assertEquals(user.getId(), ((Number) wallet.get("userId")).longValue());

        List<Map<String, Object>> logs = listData("/api/admin/users/" + user.getId() + "/wallet-logs");
        assertFalse(logs.isEmpty(), "充值后应有流水");
        assertEquals("RECHARGE", logs.get(0).get("type"));
        assertEquals(100.0, ((Number) logs.get(0).get("amount")).doubleValue(), 0.001);
        assertTrue(logs.stream().allMatch(row -> ((Number) row.get("userId")).longValue() == user.getId()),
                "单用户流水不得混入他人记录");
    }

    @Test
    void admin_filters_platform_wallet_logs_by_user_type_and_time() {
        User first = register("fund_log_a", "流水甲");
        User second = register("fund_log_b", "流水乙");
        walletService.recharge(first.getId(), new BigDecimal("30.00"));
        walletService.recharge(second.getId(), new BigDecimal("40.00"));

        List<Map<String, Object>> firstOnly = listData("/api/admin/wallet-logs?userId=" + first.getId());
        assertFalse(firstOnly.isEmpty());
        assertTrue(firstOnly.stream()
                        .allMatch(row -> ((Number) row.get("userId")).longValue() == first.getId()),
                "userId 筛选不得混入他人流水");

        List<Map<String, Object>> recharges = listData("/api/admin/wallet-logs?type=RECHARGE");
        assertFalse(recharges.isEmpty());
        assertTrue(recharges.stream().allMatch(row -> "RECHARGE".equals(row.get("type"))),
                "type 筛选后应全为 RECHARGE");

        assertTrue(listData("/api/admin/wallet-logs?to=2020-01-01T00:00:00").isEmpty(),
                "时间上界在过去时应为空");
    }

    @Test
    void frozen_summary_stays_conserved_across_every_fund_path() {
        double before = diff();

        // 路径 1：发布悬赏 -> 买方余额转冻结，同时在途多一笔同额订单
        User publisher = register("fund_frz_pub", "冻结买方");
        walletService.recharge(publisher.getId(), new BigDecimal("100.00"));
        TradePost bounty = publishBounty(publisher, "20.00");
        assertEquals(before, diff(), 0.001, "发布冻结后差额不应变化");

        // 路径 2：接单 -> 只改状态，不动金额
        User acceptor = register("fund_frz_sel", "冻结卖方");
        TradeOrder order = orderOf(bounty);
        tradeOrderService.accept(order.getId(), acceptor.getId());
        assertEquals(before, diff(), 0.001, "接单后差额不应变化");

        // 路径 3：交付 + 确认 -> 冻结释放、卖方入账，在途同步减少
        tradeOrderService.deliver(order.getId(), acceptor.getId());
        tradeOrderService.confirm(order.getId(), publisher.getId());
        assertEquals(before, diff(), 0.001, "正常结算后差额不应变化");

        // 路径 4：发布后取消 -> 冻结退回，在途移除
        User cancelPublisher = register("fund_cancel_pub", "取消买方");
        walletService.recharge(cancelPublisher.getId(), new BigDecimal("50.00"));
        TradeOrder cancelOrder = orderOf(publishBounty(cancelPublisher, "15.00"));
        tradeOrderService.cancel(cancelOrder.getId(), cancelPublisher.getId());
        assertEquals(before, diff(), 0.001, "取消解冻后差额不应变化");

        Map<String, Object> summary = frozenSummary();
        boolean diffIsZero = ((Number) summary.get("diff")).doubleValue() == 0.0;
        assertEquals(diffIsZero, Boolean.TRUE.equals(summary.get("balanced")),
                "balanced 字段必须与 diff 是否为 0 一致");
        assertTrue(((Number) summary.get("inFlightOrderCount")).longValue() >= 0);
    }

    @Test
    void user_list_never_serializes_credentials() {
        register("fund_mask", "脱敏用户");

        String raw = rest.exchange(url("/api/admin/users?keyword=fund_mask"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(admin())), String.class).getBody();

        assertFalse(raw.contains("passwordHash"), "用户列表不得出现 passwordHash 字段");
        assertFalse(raw.contains("password_hash"), "用户列表不得出现 password_hash 字段");
        assertFalse(raw.contains(userRepository.findByUsername("fund_mask").orElseThrow().getPasswordHash()),
                "用户列表不得包含真实密码哈希");
    }

    @Test
    void student_cannot_read_any_admin_fund_view() {
        User student = register("fund_forbidden", "越权用户");
        String[] paths = {
                "/api/admin/users",
                "/api/admin/users/1/wallet",
                "/api/admin/users/1/wallet-logs",
                "/api/admin/wallet-logs",
                "/api/admin/wallets/frozen-summary"
        };
        for (String path : paths) {
            ResponseEntity<Map> response = rest.exchange(url(path), HttpMethod.GET,
                    new HttpEntity<>(authHeaders(student)), Map.class);
            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(), "学生访问 " + path + " 应被拒绝");
        }
    }

    @Test
    void unknown_user_returns_business_error_instead_of_500() {
        ResponseEntity<Map> wallet = rest.exchange(url("/api/admin/users/99999999/wallet"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(admin())), Map.class);
        assertEquals(HttpStatus.OK, wallet.getStatusCode());
        assertEquals(40000, ((Number) wallet.getBody().get("code")).intValue());

        ResponseEntity<Map> logs = rest.exchange(url("/api/admin/users/99999999/wallet-logs"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(admin())), Map.class);
        assertEquals(40000, ((Number) logs.getBody().get("code")).intValue());
    }

    // ---------- 辅助 ----------

    private List<Map<String, Object>> listUsers(String query) {
        return listData("/api/admin/users?" + query);
    }

    private List<Map<String, Object>> listData(String path) {
        ResponseEntity<Map> response = rest.exchange(url(path), HttpMethod.GET,
                new HttpEntity<>(authHeaders(admin())), Map.class);
        assertEquals(0, ((Number) response.getBody().get("code")).intValue(), path + " 应返回 code=0");
        return (List<Map<String, Object>>) response.getBody().get("data");
    }

    private Map<String, Object> dataOf(String path) {
        ResponseEntity<Map> response = rest.exchange(url(path), HttpMethod.GET,
                new HttpEntity<>(authHeaders(admin())), Map.class);
        assertEquals(0, ((Number) response.getBody().get("code")).intValue(), path + " 应返回 code=0");
        return (Map<String, Object>) response.getBody().get("data");
    }

    private Map<String, Object> frozenSummary() {
        return dataOf("/api/admin/wallets/frozen-summary");
    }

    private double diff() {
        return ((Number) frozenSummary().get("diff")).doubleValue();
    }

    private List<String> usernamesOf(List<Map<String, Object>> users) {
        return users.stream().map(row -> String.valueOf(row.get("username"))).toList();
    }

    private TradeOrder orderOf(TradePost post) {
        return orderRepository.findByPostId(post.getId()).orElseThrow();
    }

    private TradePost publishBounty(User publisher, String price) {
        PublishReq request = new PublishReq();
        request.setTitle("资金监管验证悬赏");
        request.setDescription("冻结差额守恒验证");
        request.setPrice(new BigDecimal(price));
        request.setCategory("学习");
        request.setDeadline(LocalDateTime.now().plusDays(7));
        return tradePostService.publish(publisher.getId(), request);
    }

    private User admin() {
        return userRepository.findByUsername("admin").orElseThrow();
    }

    private User register(String username, String nickname) {
        return userService.register(username, "123456", "123456", nickname);
    }

    private HttpHeaders authHeaders(User user) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtUtil.generate(user.getId(), user.getUsername()));
        return headers;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
