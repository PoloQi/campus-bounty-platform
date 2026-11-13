package com.campus.bounty.trade;

import com.campus.bounty.common.BizException;
import com.campus.bounty.config.JwtUtil;
import com.campus.bounty.trade.dto.OrderDetailView;
import com.campus.bounty.trade.dto.ServicePublishReq;
import com.campus.bounty.user.User;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void buyer_can_buy_and_read_order_detail_over_http_but_outsider_cannot() {
        User publisher = register("service_buy_http_publisher");
        User buyer = register("service_buy_http_buyer");
        User outsider = register("service_buy_http_outsider");
        TradePost post = publish(publisher, "12.00");
        walletService.recharge(buyer.getId(), new BigDecimal("20.00"));

        ResponseEntity<Map> bought = rest.postForEntity(url("/api/trade/posts/" + post.getId() + "/buy"),
                new HttpEntity<>(authHeaders(buyer)), Map.class);
        assertEquals(HttpStatus.OK, bought.getStatusCode());
        assertEquals(0, code(bought));
        long orderId = ((Number) ((Map<String, Object>) bought.getBody().get("data")).get("id")).longValue();

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

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private int code(ResponseEntity<Map> response) {
        return ((Number) response.getBody().get("code")).intValue();
    }
}
