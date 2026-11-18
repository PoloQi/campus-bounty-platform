package com.campus.bounty.trade;

import com.campus.bounty.admin.AdminService;
import com.campus.bounty.common.BizException;
import com.campus.bounty.config.JwtUtil;
import com.campus.bounty.notification.NotificationRepository;
import com.campus.bounty.trade.dto.OrderDetailView;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class ServiceBuyTest {
    @LocalServerPort
    int port;
    @Autowired
    TestRestTemplate rest;
    @Autowired
    TradePostService postService;
    @Autowired
    TradeOrderService orderService;
    @Autowired
    TradeOrderRepository orderRepository;
    @Autowired
    WalletService walletService;
    @Autowired
    UserService userService;
    @Autowired
    JwtUtil jwtUtil;
    @Autowired
    AdminService adminService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    NotificationRepository notifications;
    @Autowired
    TransactionTemplate transactions;

    @Test
    void buy_creates_in_progress_order_and_freezes_buyer_funds() {
        User publisher = register("service_buy_publisher");
        User buyer = register("service_buy_buyer");
        walletService.recharge(buyer.getId(), new BigDecimal("50.00"));
        TradePost post = publish(publisher, "30.00");

        TradeOrder order = orderService.buy(post.getId(), buyer.getId());

        assertEquals(OrderStatus.IN_PROGRESS, order.getStatus());
        assertEquals(buyer.getId(), order.getBuyerId());
        assertEquals(publisher.getId(), order.getSellerId());
        assertEquals(0, order.getAmount().compareTo(new BigDecimal("30.00")));
        assertEquals(0, walletService.getOrCreate(buyer.getId()).getBalance()
                .compareTo(new BigDecimal("20.00")));
        assertEquals(0, walletService.getOrCreate(buyer.getId()).getFrozen()
                .compareTo(new BigDecimal("30.00")));
        assertTrue(walletService.logs(buyer.getId()).stream().anyMatch(log ->
                log.getType().equals("FREEZE") && log.getOrderId().equals(order.getId())));
    }

    @Test
    void buy_rejects_self_purchase_and_insufficient_balance_without_orders() {
        User publisher = register("service_buy_self_publisher");
        User poorBuyer = register("service_buy_poor_buyer");
        TradePost post = publish(publisher, "30.00");
        walletService.recharge(poorBuyer.getId(), new BigDecimal("5.00"));
        long ordersBefore = orderRepository.count();

        assertThrows(BizException.class, () -> orderService.buy(post.getId(), publisher.getId()));
        assertThrows(BizException.class, () -> orderService.buy(post.getId(), poorBuyer.getId()));

        assertEquals(ordersBefore, orderRepository.count());
        assertEquals(0, walletService.getOrCreate(poorBuyer.getId()).getFrozen().compareTo(BigDecimal.ZERO));
    }

    @Test
    void multiple_buyers_create_independent_orders_and_service_detail_has_no_order() {
        User publisher = register("service_buy_multi_publisher");
        User firstBuyer = register("service_buy_multi_first");
        User secondBuyer = register("service_buy_multi_second");
        walletService.recharge(firstBuyer.getId(), new BigDecimal("40.00"));
        walletService.recharge(secondBuyer.getId(), new BigDecimal("40.00"));
        TradePost post = publish(publisher, "20.00");

        TradeOrder first = orderService.buy(post.getId(), firstBuyer.getId());
        TradeOrder second = orderService.buy(post.getId(), secondBuyer.getId());

        assertNotEquals(first.getId(), second.getId());
        assertEquals(0, walletService.getOrCreate(firstBuyer.getId()).getFrozen()
                .compareTo(new BigDecimal("20.00")));
        assertEquals(0, walletService.getOrCreate(secondBuyer.getId()).getFrozen()
                .compareTo(new BigDecimal("20.00")));
        assertNull(postService.detail(post.getId()).getOrder());
        assertEquals(PostStatus.OPEN, post.getStatus());
    }

    @Test
    void buy_waits_for_in_flight_delisting_and_then_rejects_closed_post() throws Exception {
        User publisher = register("service_delist_publisher");
        User buyer = register("service_delist_buyer");
        User admin = register("service_delist_admin");
        admin.setRole("ADMIN");
        userRepository.save(admin);
        walletService.recharge(buyer.getId(), new BigDecimal("50.00"));
        TradePost post = publish(publisher, "20.00");

        var executor = Executors.newSingleThreadExecutor();
        try {
            Future<TradeOrder> buy = transactions.execute(status -> {
                adminService.closeTradePost(admin.getId(), post.getId());
                var starting = new CountDownLatch(1);
                Future<TradeOrder> task = executor.submit(() -> {
                    starting.countDown();
                    return orderService.buy(post.getId(), buyer.getId());
                });
                try {
                    assertTrue(starting.await(2, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
                assertThrows(TimeoutException.class, () -> task.get(300, TimeUnit.MILLISECONDS));
                return task;
            });
            ExecutionException rejected = assertThrows(ExecutionException.class, () -> buy.get(3, TimeUnit.SECONDS));
            assertEquals("服务已下架", assertInstanceOf(BizException.class, rejected.getCause()).getMessage());
        } finally {
            executor.shutdownNow();
        }
        assertTrue(orderService.ordersAsBuyer(buyer.getId()).isEmpty());
        assertEquals(0, walletService.getOrCreate(buyer.getId()).getFrozen().compareTo(BigDecimal.ZERO));
    }

    @Test
    void concurrent_http_retries_create_one_order_and_new_request_can_repurchase() throws Exception {
        User publisher = register("service_buy_retry_publisher");
        User buyer = register("service_buy_retry_buyer");
        walletService.recharge(buyer.getId(), new BigDecimal("45.00"));
        TradePost post = publish(publisher, "20.00");
        String requestId = UUID.randomUUID().toString();
        String path = "/api/trade/posts/" + post.getId() + "/buy";

        assertEquals(HttpStatus.BAD_REQUEST, buy(path, buyer, null).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, buy(path, buyer, "not-a-uuid").getStatusCode());

        var executor = Executors.newFixedThreadPool(2);
        long orderId;
        try {
            var start = new CountDownLatch(1);
            var first = executor.submit(() -> { start.await(); return buy(path, buyer, requestId); });
            var retry = executor.submit(() -> { start.await(); return buy(path, buyer, requestId); });
            start.countDown();
            orderId = purchasedOrderId(first.get(5, TimeUnit.SECONDS));
            assertEquals(orderId, purchasedOrderId(retry.get(5, TimeUnit.SECONDS)));
        } finally {
            executor.shutdownNow();
        }
        assertEquals(1, orderService.ordersAsBuyer(buyer.getId()).size());
        assertEquals(0, walletService.getOrCreate(buyer.getId()).getFrozen().compareTo(new BigDecimal("20.00")));
        assertEquals(1, walletService.logs(buyer.getId()).stream().filter(log -> log.getType().equals("FREEZE")).count());
        assertEquals(1, notifications.findByUserIdOrderByIdDesc(publisher.getId()).stream()
                .filter(notice -> notice.getType().equals("ORDER_CREATED")).count());

        var independent = buy(path, buyer, UUID.randomUUID().toString());
        assertNotEquals(orderId, purchasedOrderId(independent));
        assertEquals(2, orderService.ordersAsBuyer(buyer.getId()).size());

        User admin = register("service_buy_retry_admin");
        admin.setRole("ADMIN");
        userRepository.save(admin);
        adminService.closeTradePost(admin.getId(), post.getId());
        assertEquals(orderId, purchasedOrderId(buy(path, buyer, requestId)));
        assertEquals(2, orderService.ordersAsBuyer(buyer.getId()).size());
    }

    @Test
    void buyer_can_buy_and_read_order_detail_over_http_but_outsider_cannot() {
        User publisher = register("service_buy_http_publisher");
        User buyer = register("service_buy_http_buyer");
        User outsider = register("service_buy_http_outsider");
        TradePost post = publish(publisher, "12.00");
        walletService.recharge(buyer.getId(), new BigDecimal("20.00"));

        var bought = buy("/api/trade/posts/" + post.getId() + "/buy", buyer, UUID.randomUUID().toString());
        long orderId = purchasedOrderId(bought);

        ResponseEntity<Map> detail = rest.exchange(url("/api/trade/orders/" + orderId),
                HttpMethod.GET, new HttpEntity<>(authHeaders(buyer)), Map.class);
        assertEquals(HttpStatus.OK, detail.getStatusCode());
        assertEquals(0, code(detail));
        Map<String, Object> data = (Map<String, Object>) detail.getBody().get("data");
        assertEquals("IN_PROGRESS", ((Map<String, Object>) data.get("order")).get("status"));

        ResponseEntity<Map> forbidden = rest.exchange(url("/api/trade/orders/" + orderId),
                HttpMethod.GET, new HttpEntity<>(authHeaders(outsider)), Map.class);
        assertEquals(HttpStatus.OK, forbidden.getStatusCode());
        assertEquals(40000, code(forbidden));

        OrderDetailView direct = orderService.detailForParticipant(orderId, buyer.getId());
        assertEquals(orderId, direct.getOrder().getId());
        assertEquals(post.getId(), direct.getPost().getId());
    }

    private TradePost publish(User publisher, String price) {
        ServicePublishReq request = new ServicePublishReq();
        request.setTitle("高数一对一辅导");
        request.setDescription("两小时线上辅导");
        request.setPrice(new BigDecimal(price));
        request.setCategory("学习");
        return postService.publishService(publisher.getId(), request);
    }

    private User register(String username) {
        return userService.register(username, "123456", "123456", "服务下单测试");
    }

    private HttpHeaders authHeaders(User user) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtUtil.generate(user.getId(), user.getUsername()));
        return headers;
    }

    private ResponseEntity<Map> buy(String path, User buyer, String requestId) {
        var headers = authHeaders(buyer);
        if (requestId != null) headers.set("Idempotency-Key", requestId);
        return rest.postForEntity(url(path), new HttpEntity<>(headers), Map.class);
    }

    private long purchasedOrderId(ResponseEntity<Map> response) {
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, code(response));
        var order = (Map<?, ?>) response.getBody().get("data");
        assertFalse(order.containsKey("purchaseRequestId"));
        return ((Number) order.get("id")).longValue();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private int code(ResponseEntity<Map> response) {
        return ((Number) response.getBody().get("code")).intValue();
    }
}
