package com.campus.bounty.trade;

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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class MyOrdersTest {
    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired TradePostService postService;
    @Autowired TradeOrderService orderService;
    @Autowired TradeOrderRepository orderRepository;
    @Autowired WalletService walletService;
    @Autowired UserService userService;
    @Autowired JwtUtil jwtUtil;

    @Test
    void buyer_seller_and_published_views_only_contain_own_records() {
        User buyer = register("mine_buyer");
        User seller = register("mine_seller");
        User otherBuyer = register("mine_other_buyer");
        TradeOrder ownOrder = newPendingOrder(buyer, "我的悬赏");
        orderService.accept(ownOrder.getId(), seller.getId());
        TradeOrder otherOrder = newPendingOrder(otherBuyer, "他人的悬赏");

        assertTrue(orderService.ordersAsBuyer(buyer.getId()).stream()
                .anyMatch(order -> order.getId().equals(ownOrder.getId())));
        assertTrue(orderService.ordersAsBuyer(buyer.getId()).stream()
                .noneMatch(order -> order.getId().equals(otherOrder.getId())));
        assertTrue(orderService.ordersAsSeller(seller.getId()).stream()
                .anyMatch(order -> order.getId().equals(ownOrder.getId())));
        assertTrue(orderService.ordersAsBuyer(seller.getId()).stream()
                .noneMatch(order -> order.getId().equals(ownOrder.getId())));
        assertTrue(postService.hallMine(buyer.getId()).stream()
                .anyMatch(post -> post.getId().equals(ownOrder.getPostId())));
        assertTrue(postService.hallMine(buyer.getId()).stream()
                .noneMatch(post -> post.getId().equals(otherOrder.getPostId())));
    }

    @Test
    void personal_center_returns_all_views_for_authenticated_user() {
        User buyer = register("mine_http_buyer");
        User seller = register("mine_http_seller");
        TradeOrder order = newPendingOrder(buyer, "HTTP个人中心");
        orderService.accept(order.getId(), seller.getId());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtUtil.generate(buyer.getId(), buyer.getUsername()));

        ResponseEntity<Map> response = rest.exchange(
                "http://localhost:" + port + "/api/trade/orders/mine", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, ((Number) response.getBody().get("code")).intValue());
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        List<Map<String, Object>> asBuyer = (List<Map<String, Object>>) data.get("asBuyer");
        List<Map<String, Object>> asSeller = (List<Map<String, Object>>) data.get("asSeller");
        List<Map<String, Object>> published = (List<Map<String, Object>>) data.get("published");
        assertEquals(1, asBuyer.size());
        assertEquals(order.getId().longValue(), ((Number) asBuyer.get(0).get("id")).longValue());
        assertEquals("IN_PROGRESS", asBuyer.get(0).get("status"));
        assertTrue(asSeller.isEmpty());
        assertEquals(1, published.size());
        assertEquals(order.getPostId().longValue(), ((Number) published.get(0).get("id")).longValue());
        assertEquals("LOCKED", published.get(0).get("status"));
    }

    private TradeOrder newPendingOrder(User buyer, String title) {
        walletService.recharge(buyer.getId(), new BigDecimal("100.00"));
        PublishReq request = new PublishReq();
        request.setTitle(title);
        request.setDescription("个人中心测试描述");
        request.setPrice(new BigDecimal("5.00"));
        request.setCategory("跑腿");
        request.setDeadline(LocalDateTime.now().plusDays(1));
        TradePost post = postService.publish(buyer.getId(), request);
        return orderRepository.findByPostIdAndStatusNot(post.getId(), OrderStatus.CANCELLED).orElseThrow();
    }

    private User register(String username) {
        return userService.register(username, "123456", "123456", "个人中心测试");
    }
}
