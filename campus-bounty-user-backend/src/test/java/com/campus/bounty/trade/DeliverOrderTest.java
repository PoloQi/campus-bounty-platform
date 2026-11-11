package com.campus.bounty.trade;

import com.campus.bounty.common.BizException;
import com.campus.bounty.config.JwtUtil;
import com.campus.bounty.trade.dto.PublishReq;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class DeliverOrderTest {
    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired TradePostService postService;
    @Autowired TradeOrderService orderService;
    @Autowired TradeOrderRepository orderRepository;
    @Autowired WalletService walletService;
    @Autowired UserService userService;
    @Autowired JwtUtil jwtUtil;

    @Test
    void deliver_moves_to_confirming_and_keeps_reward_frozen() {
        User buyer = register("deliver_buyer");
        User seller = register("deliver_seller");
        TradeOrder order = newInProgressOrder(buyer, seller, "交付成功");

        TradeOrder delivered = orderService.deliver(order.getId(), seller.getId());

        assertEquals(OrderStatus.CONFIRMING, delivered.getStatus());
        assertEquals(0, walletService.getOrCreate(buyer.getId()).getFrozen()
                .compareTo(new BigDecimal("5.00")));
    }

    @Test
    void deliver_rejects_non_seller_without_changing_status() {
        User buyer = register("deliver_other_buyer");
        User seller = register("deliver_other_seller");
        User other = register("deliver_other_user");
        TradeOrder order = newInProgressOrder(buyer, seller, "禁止他人交付");

        assertThrows(BizException.class, () -> orderService.deliver(order.getId(), other.getId()));

        assertEquals(OrderStatus.IN_PROGRESS,
                orderRepository.findById(order.getId()).orElseThrow().getStatus());
    }

    @Test
    void deliver_rejects_repeated_delivery() {
        User buyer = register("deliver_repeat_buyer");
        User seller = register("deliver_repeat_seller");
        TradeOrder order = newInProgressOrder(buyer, seller, "禁止重复交付");
        orderService.deliver(order.getId(), seller.getId());

        assertThrows(BizException.class, () -> orderService.deliver(order.getId(), seller.getId()));

        assertEquals(OrderStatus.CONFIRMING,
                orderRepository.findById(order.getId()).orElseThrow().getStatus());
    }

    @Test
    void authenticated_seller_can_deliver_over_http() {
        User buyer = register("deliver_http_buyer");
        User seller = register("deliver_http_seller");
        TradeOrder order = newInProgressOrder(buyer, seller, "HTTP交付");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtUtil.generate(seller.getId(), seller.getUsername()));

        ResponseEntity<Map> response = rest.postForEntity(
                "http://localhost:" + port + "/api/trade/orders/" + order.getId() + "/deliver",
                new HttpEntity<>(headers), Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, ((Number) response.getBody().get("code")).intValue());
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertEquals("CONFIRMING", data.get("status"));
    }

    private TradeOrder newInProgressOrder(User buyer, User seller, String title) {
        walletService.recharge(buyer.getId(), new BigDecimal("100.00"));
        PublishReq request = new PublishReq();
        request.setTitle(title);
        request.setDescription("交付测试描述");
        request.setPrice(new BigDecimal("5.00"));
        request.setCategory("跑腿");
        request.setDeadline(LocalDateTime.now().plusDays(1));
        TradePost post = postService.publish(buyer.getId(), request);
        TradeOrder order = orderRepository.findByPostIdAndStatusNot(post.getId(), OrderStatus.CANCELLED)
                .orElseThrow();
        return orderService.accept(order.getId(), seller.getId());
    }

    private User register(String username) {
        return userService.register(username, "123456", "123456", "交付测试");
    }
}
