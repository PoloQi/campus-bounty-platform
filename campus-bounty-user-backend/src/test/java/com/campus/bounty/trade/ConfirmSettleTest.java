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
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class ConfirmSettleTest {
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
    void confirm_settles_reward_closes_post_and_writes_logs() {
        User buyer = register("confirm_buyer");
        User seller = register("confirm_seller");
        TradeOrder order = newConfirmingOrder(buyer, seller, "确认结算");

        TradeOrder completed = orderService.confirm(order.getId(), buyer.getId());

        assertEquals(OrderStatus.COMPLETED, completed.getStatus());
        assertEquals(PostStatus.CLOSED, postRepository.findById(order.getPostId()).orElseThrow().getStatus());
        assertEquals(0, walletService.getOrCreate(buyer.getId()).getBalance()
                .compareTo(new BigDecimal("70.00")));
        assertEquals(0, walletService.getOrCreate(buyer.getId()).getFrozen().compareTo(BigDecimal.ZERO));
        assertEquals(0, walletService.getOrCreate(seller.getId()).getBalance()
                .compareTo(new BigDecimal("30.00")));
        assertTrue(walletService.logs(buyer.getId()).stream().anyMatch(log ->
                log.getType().equals("PAY")
                        && log.getOrderId().equals(order.getId())
                        && log.getAmount().compareTo(new BigDecimal("-30.00")) == 0
                        && log.getBalanceAfter().compareTo(new BigDecimal("70.00")) == 0));
        assertTrue(walletService.logs(seller.getId()).stream().anyMatch(log ->
                log.getType().equals("INCOME")
                        && log.getOrderId().equals(order.getId())
                        && log.getAmount().compareTo(new BigDecimal("30.00")) == 0
                        && log.getBalanceAfter().compareTo(new BigDecimal("30.00")) == 0));
    }

    @Test
    void confirm_rejects_non_buyer_without_settlement() {
        User buyer = register("confirm_other_buyer");
        User seller = register("confirm_other_seller");
        TradeOrder order = newConfirmingOrder(buyer, seller, "禁止他人确认");

        assertThrows(BizException.class, () -> orderService.confirm(order.getId(), seller.getId()));

        assertEquals(OrderStatus.CONFIRMING,
                orderRepository.findById(order.getId()).orElseThrow().getStatus());
        assertEquals(0, walletService.getOrCreate(buyer.getId()).getFrozen()
                .compareTo(new BigDecimal("30.00")));
        assertEquals(0, walletService.getOrCreate(seller.getId()).getBalance().compareTo(BigDecimal.ZERO));
    }

    @Test
    void confirm_rejects_repeated_confirmation_without_duplicate_logs() {
        User buyer = register("confirm_repeat_buyer");
        User seller = register("confirm_repeat_seller");
        TradeOrder order = newConfirmingOrder(buyer, seller, "禁止重复结算");
        orderService.confirm(order.getId(), buyer.getId());

        assertThrows(BizException.class, () -> orderService.confirm(order.getId(), buyer.getId()));

        assertEquals(1, walletService.logs(buyer.getId()).stream()
                .filter(log -> log.getType().equals("PAY")).count());
        assertEquals(1, walletService.logs(seller.getId()).stream()
                .filter(log -> log.getType().equals("INCOME")).count());
        assertEquals(0, walletService.getOrCreate(seller.getId()).getBalance()
                .compareTo(new BigDecimal("30.00")));
    }

    @Test
    void authenticated_buyer_can_confirm_over_http() {
        User buyer = register("confirm_http_buyer");
        User seller = register("confirm_http_seller");
        TradeOrder order = newConfirmingOrder(buyer, seller, "HTTP确认");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtUtil.generate(buyer.getId(), buyer.getUsername()));

        ResponseEntity<Map> response = rest.postForEntity(
                "http://localhost:" + port + "/api/trade/orders/" + order.getId() + "/confirm",
                new HttpEntity<>(headers), Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, ((Number) response.getBody().get("code")).intValue());
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertEquals("COMPLETED", data.get("status"));
        assertEquals(0, walletService.getOrCreate(seller.getId()).getBalance()
                .compareTo(new BigDecimal("30.00")));
    }

    private TradeOrder newConfirmingOrder(User buyer, User seller, String title) {
        walletService.recharge(buyer.getId(), new BigDecimal("100.00"));
        PublishReq request = new PublishReq();
        request.setTitle(title);
        request.setDescription("确认结算测试描述");
        request.setPrice(new BigDecimal("30.00"));
        request.setCategory("跑腿");
        request.setDeadline(LocalDateTime.now().plusDays(1));
        TradePost post = postService.publish(buyer.getId(), request);
        TradeOrder order = orderRepository.findByPostIdAndStatusNot(post.getId(), OrderStatus.CANCELLED)
                .orElseThrow();
        orderService.accept(order.getId(), seller.getId());
        return orderService.deliver(order.getId(), seller.getId());
    }

    private User register(String username) {
        return userService.register(username, "123456", "123456", "确认结算测试");
    }
}
