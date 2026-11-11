package com.campus.bounty.trade;

import com.campus.bounty.common.BizException;
import com.campus.bounty.config.JwtUtil;
import com.campus.bounty.trade.dto.PostView;
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
class CancelOrderTest {
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
    void cancel_pending_unfreezes_reward_and_closes_post() {
        User buyer = register("cancel_pending_buyer");
        TradeOrder order = newPendingOrder(buyer, "取消待接单悬赏");

        TradeOrder cancelled = orderService.cancel(order.getId(), buyer.getId());

        assertCancelledAndRefunded(buyer, order, cancelled);
        assertTrue(walletService.logs(buyer.getId()).stream().anyMatch(log ->
                log.getType().equals("UNFREEZE")
                        && log.getOrderId().equals(order.getId())
                        && log.getAmount().compareTo(new BigDecimal("40.00")) == 0
                        && log.getBalanceAfter().compareTo(new BigDecimal("100.00")) == 0));
    }

    @Test
    void detail_returns_cancelled_order_after_cancellation() {
        User buyer = register("cancel_detail_buyer");
        TradeOrder order = newPendingOrder(buyer, "取消后查看详情");
        orderService.cancel(order.getId(), buyer.getId());

        PostView detail = postService.detail(order.getPostId());

        assertEquals(OrderStatus.CANCELLED, detail.getOrder().getStatus());
    }

    @Test
    void cancel_in_progress_unfreezes_reward_and_closes_post() {
        User buyer = register("cancel_progress_buyer");
        User seller = register("cancel_progress_seller");
        TradeOrder order = newPendingOrder(buyer, "取消进行中悬赏");
        orderService.accept(order.getId(), seller.getId());

        TradeOrder cancelled = orderService.cancel(order.getId(), buyer.getId());

        assertCancelledAndRefunded(buyer, order, cancelled);
        assertEquals(0, walletService.getOrCreate(seller.getId()).getBalance().compareTo(BigDecimal.ZERO));
    }

    @Test
    void cancel_rejects_non_buyer_without_refund() {
        User buyer = register("cancel_other_buyer");
        User other = register("cancel_other_user");
        TradeOrder order = newPendingOrder(buyer, "禁止他人取消");

        assertThrows(BizException.class, () -> orderService.cancel(order.getId(), other.getId()));

        assertEquals(OrderStatus.PENDING, orderRepository.findById(order.getId()).orElseThrow().getStatus());
        assertEquals(PostStatus.OPEN, postRepository.findById(order.getPostId()).orElseThrow().getStatus());
        assertEquals(0, walletService.getOrCreate(buyer.getId()).getFrozen()
                .compareTo(new BigDecimal("40.00")));
    }

    @Test
    void cancel_rejects_confirming_order_without_refund() {
        User buyer = register("cancel_confirming_buyer");
        User seller = register("cancel_confirming_seller");
        TradeOrder order = newPendingOrder(buyer, "待确认禁止取消");
        orderService.accept(order.getId(), seller.getId());
        orderService.deliver(order.getId(), seller.getId());

        assertThrows(BizException.class, () -> orderService.cancel(order.getId(), buyer.getId()));

        assertEquals(OrderStatus.CONFIRMING,
                orderRepository.findById(order.getId()).orElseThrow().getStatus());
        assertEquals(PostStatus.LOCKED, postRepository.findById(order.getPostId()).orElseThrow().getStatus());
        assertEquals(0, walletService.getOrCreate(buyer.getId()).getFrozen()
                .compareTo(new BigDecimal("40.00")));
    }

    @Test
    void cancel_rejects_repeated_cancellation_without_duplicate_refund() {
        User buyer = register("cancel_repeat_buyer");
        TradeOrder order = newPendingOrder(buyer, "禁止重复取消");
        orderService.cancel(order.getId(), buyer.getId());

        assertThrows(BizException.class, () -> orderService.cancel(order.getId(), buyer.getId()));

        assertEquals(1, walletService.logs(buyer.getId()).stream()
                .filter(log -> log.getType().equals("UNFREEZE")).count());
        assertEquals(0, walletService.getOrCreate(buyer.getId()).getBalance()
                .compareTo(new BigDecimal("100.00")));
    }

    @Test
    void authenticated_buyer_can_cancel_over_http() {
        User buyer = register("cancel_http_buyer");
        TradeOrder order = newPendingOrder(buyer, "HTTP取消");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtUtil.generate(buyer.getId(), buyer.getUsername()));

        ResponseEntity<Map> response = rest.postForEntity(
                "http://localhost:" + port + "/api/trade/orders/" + order.getId() + "/cancel",
                new HttpEntity<>(headers), Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, ((Number) response.getBody().get("code")).intValue());
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertEquals("CANCELLED", data.get("status"));
        assertEquals(0, walletService.getOrCreate(buyer.getId()).getBalance()
                .compareTo(new BigDecimal("100.00")));
    }

    private void assertCancelledAndRefunded(User buyer, TradeOrder order, TradeOrder cancelled) {
        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
        assertEquals(PostStatus.CLOSED, postRepository.findById(order.getPostId()).orElseThrow().getStatus());
        assertEquals(0, walletService.getOrCreate(buyer.getId()).getBalance()
                .compareTo(new BigDecimal("100.00")));
        assertEquals(0, walletService.getOrCreate(buyer.getId()).getFrozen().compareTo(BigDecimal.ZERO));
    }

    private TradeOrder newPendingOrder(User buyer, String title) {
        walletService.recharge(buyer.getId(), new BigDecimal("100.00"));
        PublishReq request = new PublishReq();
        request.setTitle(title);
        request.setDescription("取消测试描述");
        request.setPrice(new BigDecimal("40.00"));
        request.setCategory("跑腿");
        request.setDeadline(LocalDateTime.now().plusDays(1));
        TradePost post = postService.publish(buyer.getId(), request);
        return orderRepository.findByPostIdAndStatusNot(post.getId(), OrderStatus.CANCELLED).orElseThrow();
    }

    private User register(String username) {
        return userService.register(username, "123456", "123456", "取消测试");
    }
}
