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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class AcceptOrderTest {
    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired TradePostService postService;
    @Autowired TradeOrderService orderService;
    @Autowired TradeOrderRepository orderRepository;
    @Autowired TradePostRepository postRepository;
    @Autowired WalletService walletService;
    @Autowired UserService userService;
    @Autowired JwtUtil jwtUtil;

    @Test
    void accept_sets_seller_in_progress_and_locks_post() {
        User buyer = register("accept_buyer");
        User seller = register("accept_seller");
        TradeOrder order = newPendingOrder(buyer, "接单成功");

        TradeOrder accepted = orderService.accept(order.getId(), seller.getId());

        assertEquals(OrderStatus.IN_PROGRESS, accepted.getStatus());
        assertEquals(seller.getId(), accepted.getSellerId());
        assertEquals(PostStatus.LOCKED, postRepository.findById(order.getPostId()).orElseThrow().getStatus());
    }

    @Test
    void accept_rejects_publisher_self_without_changes() {
        User buyer = register("accept_self_buyer");
        TradeOrder order = newPendingOrder(buyer, "禁止自接");

        assertThrows(BizException.class, () -> orderService.accept(order.getId(), buyer.getId()));

        TradeOrder unchanged = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.PENDING, unchanged.getStatus());
        assertNull(unchanged.getSellerId());
        assertEquals(PostStatus.OPEN, postRepository.findById(order.getPostId()).orElseThrow().getStatus());
    }

    @Test
    void accept_rejects_second_accepter_without_replacing_seller() {
        User buyer = register("accept_repeat_buyer");
        User seller = register("accept_repeat_seller");
        User other = register("accept_repeat_other");
        TradeOrder order = newPendingOrder(buyer, "防止重复接单");
        orderService.accept(order.getId(), seller.getId());

        assertThrows(BizException.class, () -> orderService.accept(order.getId(), other.getId()));

        TradeOrder unchanged = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(seller.getId(), unchanged.getSellerId());
        assertEquals(OrderStatus.IN_PROGRESS, unchanged.getStatus());
    }

    @Test
    void authenticated_user_can_accept_over_http() {
        User buyer = register("accept_http_buyer");
        User seller = register("accept_http_seller");
        TradeOrder order = newPendingOrder(buyer, "HTTP接单");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtUtil.generate(seller.getId(), seller.getUsername()));

        ResponseEntity<Map> response = rest.postForEntity(
                "http://localhost:" + port + "/api/trade/orders/" + order.getId() + "/accept",
                new HttpEntity<>(headers), Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, ((Number) response.getBody().get("code")).intValue());
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertEquals("IN_PROGRESS", data.get("status"));
        assertEquals(seller.getId().longValue(), ((Number) data.get("sellerId")).longValue());
    }

    private TradeOrder newPendingOrder(User buyer, String title) {
        walletService.recharge(buyer.getId(), new BigDecimal("100.00"));
        PublishReq request = new PublishReq();
        request.setTitle(title);
        request.setDescription("接单测试描述");
        request.setPrice(new BigDecimal("5.00"));
        request.setCategory("跑腿");
        request.setDeadline(LocalDateTime.now().plusDays(1));
        TradePost post = postService.publish(buyer.getId(), request);
        return orderRepository.findByPostIdAndStatusNot(post.getId(), OrderStatus.CANCELLED).orElseThrow();
    }

    private User register(String username) {
        return userService.register(username, "123456", "123456", "接单测试");
    }
}
