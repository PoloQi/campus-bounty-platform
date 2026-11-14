package com.campus.bounty.trade;

import com.campus.bounty.common.BizException;
import com.campus.bounty.config.JwtUtil;
import com.campus.bounty.notification.NotificationService;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class AppealFlowTest {
    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired TradePostService postService;
    @Autowired TradeOrderService orderService;
    @Autowired AppealService appealService;
    @Autowired AppealRepository appealRepository;
    @Autowired TradeOrderRepository orderRepository;
    @Autowired WalletService walletService;
    @Autowired NotificationService notificationService;
    @Autowired UserService userService;
    @Autowired JwtUtil jwtUtil;

    @Test
    void open_sets_disputed_and_notifies_other_party() {
        User buyer = register("appeal_buyer_1");
        User seller = register("appeal_seller_1");
        TradeOrder order = inProgress(buyer.getId(), seller.getId());

        Appeal appeal = appealService.open(order.getId(), seller.getId(), "我已取件对方却要取消");

        assertEquals(OrderStatus.DISPUTED, orderRepository.findById(order.getId()).orElseThrow().getStatus());
        assertEquals("IN_PROGRESS", appeal.getFromStatus());
        assertEquals("OPEN", appeal.getStatus());
        assertEquals(seller.getId(), appeal.getAppellantId());
        assertTrue(notificationService.list(buyer.getId()).stream()
                .anyMatch(n -> "APPEAL_CREATED".equals(n.getType())));
    }

    @Test
    void open_rejects_empty_outsider_duplicate_and_pending() {
        User buyer = register("appeal_buyer_2");
        User seller = register("appeal_seller_2");
        User outsider = register("appeal_outsider_2");
        TradeOrder order = inProgress(buyer.getId(), seller.getId());

        assertThrows(BizException.class, () -> appealService.open(order.getId(), buyer.getId(), ""));
        assertThrows(BizException.class, () -> appealService.open(order.getId(), buyer.getId(), "   "));
        assertThrows(BizException.class, () -> appealService.open(order.getId(), buyer.getId(), "x".repeat(501)));
        assertThrows(BizException.class, () -> appealService.open(order.getId(), outsider.getId(), "外人"));

        appealService.open(order.getId(), buyer.getId(), "对方拖延");
        assertThrows(BizException.class, () -> appealService.open(order.getId(), seller.getId(), "重复申诉"));

        User pendingBuyer = register("appeal_pending_buyer_2");
        TradeOrder pending = newPendingOrder(pendingBuyer.getId());
        assertThrows(BizException.class, () -> appealService.open(pending.getId(), pendingBuyer.getId(), "还没人接单"));
    }

    @Test
    void disputed_blocks_normal_flows() {
        User buyer = register("appeal_buyer_3");
        User seller = register("appeal_seller_3");
        TradeOrder order = inProgress(buyer.getId(), seller.getId());

        appealService.open(order.getId(), seller.getId(), "送达争议");
        assertThrows(BizException.class, () -> orderService.deliver(order.getId(), seller.getId()));
        assertThrows(BizException.class, () -> orderService.cancel(order.getId(), buyer.getId()));

        Long appealId = appealRepository.findFirstByOrderIdAndStatusOrderByIdDesc(order.getId(), "OPEN")
                .orElseThrow().getId();
        appealService.withdraw(appealId, seller.getId());
        orderService.deliver(order.getId(), seller.getId());

        appealService.open(order.getId(), buyer.getId(), "未收到货");
        assertThrows(BizException.class, () -> orderService.confirm(order.getId(), buyer.getId()));
        assertThrows(BizException.class, () -> orderService.accept(order.getId(), seller.getId()));
    }

    @Test
    void withdraw_restores_status_only_appellant_once() {
        User buyer = register("appeal_buyer_4");
        User seller = register("appeal_seller_4");
        TradeOrder order = inProgress(buyer.getId(), seller.getId());

        Appeal appeal = appealService.open(order.getId(), seller.getId(), "催付");
        assertThrows(BizException.class, () -> appealService.withdraw(appeal.getId(), buyer.getId()));

        Appeal withdrawn = appealService.withdraw(appeal.getId(), seller.getId());
        assertEquals("WITHDRAWN", withdrawn.getStatus());
        assertEquals(OrderStatus.IN_PROGRESS, orderRepository.findById(order.getId()).orElseThrow().getStatus());
        assertNotNull(withdrawn.getHandledAt());
        assertTrue(notificationService.list(buyer.getId()).stream()
                .anyMatch(n -> "APPEAL_WITHDRAWN".equals(n.getType())));
        assertThrows(BizException.class, () -> appealService.withdraw(appeal.getId(), seller.getId()));
    }

    @Test
    void http_appeal_and_view_open_work_for_participants_only() {
        User buyer = register("appeal_buyer_5");
        User seller = register("appeal_seller_5");
        User outsider = register("appeal_outsider_5");
        TradeOrder order = inProgress(buyer.getId(), seller.getId());

        ResponseEntity<Map> created = rest.postForEntity(url("/api/trade/orders/" + order.getId() + "/appeal"),
                new HttpEntity<>(Map.of("reason", "已交付对方不确认"), authHeaders(seller)), Map.class);
        assertEquals(HttpStatus.OK, created.getStatusCode());
        assertEquals(0, ((Number) created.getBody().get("code")).intValue());

        ResponseEntity<Map> view = rest.getForEntity(url("/api/trade/orders/" + order.getId() + "/appeal"),
                Map.class);
        // 未带 token 的查询返回 401
        assertEquals(HttpStatus.UNAUTHORIZED, view.getStatusCode());

        ResponseEntity<Map> participantView = rest.exchange(url("/api/trade/orders/" + order.getId() + "/appeal"),
                org.springframework.http.HttpMethod.GET, new HttpEntity<>(authHeaders(buyer)), Map.class);
        assertEquals(0, ((Number) participantView.getBody().get("code")).intValue());
        Map<String, Object> data = (Map<String, Object>) participantView.getBody().get("data");
        assertEquals("OPEN", data.get("status"));
        assertEquals("IN_PROGRESS", data.get("fromStatus"));

        ResponseEntity<Map> outsiderView = rest.exchange(url("/api/trade/orders/" + order.getId() + "/appeal"),
                org.springframework.http.HttpMethod.GET, new HttpEntity<>(authHeaders(outsider)), Map.class);
        assertEquals(40000, ((Number) outsiderView.getBody().get("code")).intValue());
    }

    private TradeOrder inProgress(Long buyerId, Long sellerId) {
        TradeOrder order = newPendingOrder(buyerId);
        return orderService.accept(order.getId(), sellerId);
    }

    private TradeOrder newPendingOrder(Long buyerId) {
        walletService.recharge(buyerId, new BigDecimal("100.00"));
        PublishReq request = new PublishReq();
        request.setTitle("申诉测试");
        request.setDescription("申诉流程测试");
        request.setCategory("跑腿");
        request.setPrice(new BigDecimal("20.00"));
        request.setDeadline(LocalDateTime.now().plusDays(1));
        TradePost post = postService.publish(buyerId, request);
        return orderRepository.findByPostIdAndStatusNot(post.getId(), OrderStatus.CANCELLED).orElseThrow();
    }

    private User register(String username) {
        return userService.register(username, "123456", "123456", "申诉测试");
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
