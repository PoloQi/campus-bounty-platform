package com.campus.bounty.admin;

import com.campus.bounty.config.JwtUtil;
import com.campus.bounty.trade.AppealService;
import com.campus.bounty.trade.TradeOrder;
import com.campus.bounty.trade.TradeOrderRepository;
import com.campus.bounty.trade.TradeOrderService;
import com.campus.bounty.trade.TradePost;
import com.campus.bounty.trade.TradePostService;
import com.campus.bounty.trade.dto.PublishReq;
import com.campus.bounty.trade.dto.ServicePublishReq;
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
 * B2 订单只读板块验收。
 * <p>
 * H2 库跨测试类保留数据（DB_CLOSE_DELAY=-1），列表接口会返回其它测试类残留的订单，
 * 因此所有列表断言都用「自己的订单 id 在 / 不在结果集」表达，不做全量条数断言。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@SuppressWarnings("unchecked")
class AdminOrderQueryTest {
    @LocalServerPort
    int port;
    @Autowired
    TestRestTemplate rest;
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
    AppealService appealService;
    @Autowired
    JwtUtil jwtUtil;

    @Test
    void admin_filters_orders_by_status_type_buyer_seller_and_time() {
        User bountyPublisher = register("orderq_bounty_pub");
        User bountyAcceptor = register("orderq_bounty_seller");
        TradePost bounty = publishBounty(bountyPublisher, "20.00");
        TradeOrder acceptedOrder = orderOf(bounty);
        tradeOrderService.accept(acceptedOrder.getId(), bountyAcceptor.getId());

        TradePost pendingBounty = publishBounty(register("orderq_pending_pub"), "12.00");
        TradeOrder pendingOrder = orderOf(pendingBounty);

        User serviceSeller = register("orderq_service_pub");
        User serviceBuyer = register("orderq_service_buyer");
        TradePost service = publishService(serviceSeller, "30.00");
        walletService.recharge(serviceBuyer.getId(), new BigDecimal("100.00"));
        TradeOrder serviceOrder = tradeOrderService.buy(service.getId(), serviceBuyer.getId());

        List<Long> bounties = idsOf(listOrders("postType=BOUNTY"));
        assertTrue(bounties.contains(acceptedOrder.getId()), "BOUNTY 筛选应包含悬赏订单");
        assertFalse(bounties.contains(serviceOrder.getId()), "BOUNTY 筛选不应包含服务订单");

        List<Long> services = idsOf(listOrders("postType=SERVICE"));
        assertTrue(services.contains(serviceOrder.getId()), "SERVICE 筛选应包含服务订单");
        assertFalse(services.contains(acceptedOrder.getId()), "SERVICE 筛选不应包含悬赏订单");

        assertTrue(idsOf(listOrders("status=PENDING")).contains(pendingOrder.getId()));
        assertTrue(idsOf(listOrders("status=IN_PROGRESS")).contains(acceptedOrder.getId()));

        List<Long> byBuyer = idsOf(listOrders("buyerId=" + serviceBuyer.getId()));
        assertTrue(byBuyer.contains(serviceOrder.getId()));
        assertFalse(byBuyer.contains(acceptedOrder.getId()));

        List<Long> bySeller = idsOf(listOrders("sellerId=" + bountyAcceptor.getId()));
        assertTrue(bySeller.contains(acceptedOrder.getId()));
        assertFalse(bySeller.contains(serviceOrder.getId()));

        String past = LocalDateTime.now().minusHours(1).withNano(0)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        assertTrue(idsOf(listOrders("from=" + past)).contains(acceptedOrder.getId()),
                "from 在过去，应包含刚创建的订单");
        assertFalse(idsOf(listOrders("to=" + past)).contains(acceptedOrder.getId()),
                "to 在过去，不应包含刚创建的订单");
    }

    @Test
    void admin_order_detail_aggregates_post_participants_and_wallet_timeline() {
        User publisher = register("orderq_detail_pub");
        User acceptor = register("orderq_detail_seller");
        TradePost bounty = publishBounty(publisher, "25.00");
        TradeOrder order = orderOf(bounty);
        tradeOrderService.accept(order.getId(), acceptor.getId());

        Map<String, Object> detail = detailOf(order.getId());

        Map<String, Object> orderNode = (Map<String, Object>) detail.get("order");
        assertEquals(order.getId(), longOf(orderNode.get("id")));
        assertEquals("IN_PROGRESS", orderNode.get("status"));
        assertEquals(0, new BigDecimal("25.00").compareTo(new BigDecimal(String.valueOf(orderNode.get("amount")))));

        Map<String, Object> postNode = (Map<String, Object>) detail.get("post");
        assertEquals(bounty.getId(), longOf(postNode.get("id")));
        assertEquals(bounty.getTitle(), postNode.get("title"));
        assertEquals("BOUNTY", postNode.get("type"));

        Map<String, Object> buyerNode = (Map<String, Object>) detail.get("buyer");
        assertEquals(publisher.getUsername(), buyerNode.get("username"));
        assertEquals("ACTIVE", buyerNode.get("status"));

        Map<String, Object> sellerNode = (Map<String, Object>) detail.get("seller");
        assertEquals(acceptor.getUsername(), sellerNode.get("username"));

        List<Map<String, Object>> logs = (List<Map<String, Object>>) detail.get("walletLogs");
        assertFalse(logs.isEmpty(), "订单详情应带出冻结流水");
        assertEquals("FREEZE", logs.get(0).get("type"), "时间线第一笔是发布时的冻结");
        assertEquals(order.getId(), longOf(logs.get(0).get("orderId")));
        assertTrue(((List<?>) detail.get("appeals")).isEmpty(), "未申诉的订单不应有申诉记录");
    }

    @Test
    void admin_order_detail_includes_appeals_and_disputed_status() {
        User publisher = register("orderq_appeal_pub");
        User acceptor = register("orderq_appeal_seller");
        TradePost bounty = publishBounty(publisher, "15.00");
        TradeOrder order = orderOf(bounty);
        tradeOrderService.accept(order.getId(), acceptor.getId());
        appealService.open(order.getId(), publisher.getId(), "对方未按约定交付");

        Map<String, Object> detail = detailOf(order.getId());

        List<Map<String, Object>> appeals = (List<Map<String, Object>>) detail.get("appeals");
        assertEquals(1, appeals.size());
        assertEquals("OPEN", appeals.get(0).get("status"));
        assertEquals(publisher.getId(), longOf(appeals.get(0).get("appellantId")));
        assertEquals("DISPUTED",
                ((Map<String, Object>) detail.get("order")).get("status"), "申诉后订单应进入 DISPUTED");
    }

    @Test
    void admin_order_detail_never_serializes_credentials() {
        User publisher = register("orderq_mask_pub");
        User acceptor = register("orderq_mask_seller");
        TradePost bounty = publishBounty(publisher, "10.00");
        TradeOrder order = orderOf(bounty);
        tradeOrderService.accept(order.getId(), acceptor.getId());

        String raw = rest.exchange(url("/api/admin/orders/" + order.getId()), HttpMethod.GET,
                new HttpEntity<>(authHeaders(admin())), String.class).getBody();

        assertFalse(raw.contains("passwordHash"), "响应不得出现 passwordHash 字段");
        assertFalse(raw.contains("password_hash"), "响应不得出现 password_hash 字段");
        assertFalse(raw.contains(userRepository.findById(publisher.getId()).orElseThrow().getPasswordHash()),
                "响应不得包含真实密码哈希");
        assertFalse(raw.contains(userRepository.findById(acceptor.getId()).orElseThrow().getPasswordHash()),
                "响应不得包含接单者密码哈希");
    }

    @Test
    void student_cannot_read_admin_order_views() {
        User student = register("orderq_forbidden");

        ResponseEntity<Map> list = rest.exchange(url("/api/admin/orders"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(student)), Map.class);
        assertEquals(HttpStatus.FORBIDDEN, list.getStatusCode());
        assertEquals(40300, ((Number) list.getBody().get("code")).intValue());

        ResponseEntity<Map> detail = rest.exchange(url("/api/admin/orders/1"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(student)), Map.class);
        assertEquals(HttpStatus.FORBIDDEN, detail.getStatusCode());
    }

    @Test
    void unknown_order_returns_business_error_instead_of_500() {
        ResponseEntity<Map> response = rest.exchange(url("/api/admin/orders/99999999"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(admin())), Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(40000, ((Number) response.getBody().get("code")).intValue());
    }

    private List<Map<String, Object>> listOrders(String query) {
        String path = query == null || query.isBlank() ? "/api/admin/orders" : "/api/admin/orders?" + query;
        ResponseEntity<Map> response = rest.exchange(url(path), HttpMethod.GET,
                new HttpEntity<>(authHeaders(admin())), Map.class);
        assertEquals(0, ((Number) response.getBody().get("code")).intValue(), "列表接口应返回 code=0");
        return (List<Map<String, Object>>) response.getBody().get("data");
    }

    private Map<String, Object> detailOf(Long orderId) {
        ResponseEntity<Map> response = rest.exchange(url("/api/admin/orders/" + orderId), HttpMethod.GET,
                new HttpEntity<>(authHeaders(admin())), Map.class);
        assertEquals(0, ((Number) response.getBody().get("code")).intValue(), "详情接口应返回 code=0");
        return (Map<String, Object>) response.getBody().get("data");
    }

    private List<Long> idsOf(List<Map<String, Object>> orders) {
        return orders.stream().map(order -> longOf(order.get("id"))).toList();
    }

    private long longOf(Object value) {
        return ((Number) value).longValue();
    }

    private TradeOrder orderOf(TradePost post) {
        return orderRepository.findByPostId(post.getId()).orElseThrow();
    }

    private TradePost publishBounty(User publisher, String price) {
        walletService.recharge(publisher.getId(), new BigDecimal("100.00"));
        PublishReq request = new PublishReq();
        request.setTitle("管理端订单查询悬赏");
        request.setDescription("订单详情聚合验证用");
        request.setPrice(new BigDecimal(price));
        request.setCategory("学习");
        request.setDeadline(LocalDateTime.now().plusDays(7));
        return tradePostService.publish(publisher.getId(), request);
    }

    private TradePost publishService(User publisher, String price) {
        ServicePublishReq request = new ServicePublishReq();
        request.setTitle("管理端订单查询服务");
        request.setDescription("服务订单筛选验证用");
        request.setPrice(new BigDecimal(price));
        request.setCategory("学习");
        return tradePostService.publishService(publisher.getId(), request);
    }

    private User admin() {
        return userRepository.findByUsername("admin").orElseThrow();
    }

    private User register(String username) {
        return userService.register(username, "123456", "123456", "订单查询用户");
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
