package com.campus.bounty.notification;

import com.campus.bounty.common.BizException;
import com.campus.bounty.config.JwtUtil;
import com.campus.bounty.trade.OrderStatus;
import com.campus.bounty.trade.TradeOrder;
import com.campus.bounty.trade.TradeOrderRepository;
import com.campus.bounty.trade.TradeOrderService;
import com.campus.bounty.trade.TradePost;
import com.campus.bounty.trade.TradePostService;
import com.campus.bounty.trade.dto.PublishReq;
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
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class NotificationFlowTest {
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
    NotificationService notificationService;
    @Autowired
    JwtUtil jwtUtil;

    @Test
    void order_actions_notify_the_other_participant() {
        User buyer = register("notify_flow_buyer");
        User seller = register("notify_flow_seller");
        TradeOrder order = newPendingOrder(buyer);

        orderService.accept(order.getId(), seller.getId());
        orderService.deliver(order.getId(), seller.getId());
        orderService.confirm(order.getId(), buyer.getId());

        assertEquals(List.of("ORDER_DELIVERED", "ORDER_ACCEPTED"),
                notificationService.list(buyer.getId()).stream().map(Notification::getType).toList());
        assertEquals(List.of("ORDER_COMPLETED"),
                notificationService.list(seller.getId()).stream().map(Notification::getType).toList());
        assertTrue(notificationService.list(buyer.getId()).stream()
                .allMatch(item -> item.getRefId().equals(order.getId())));
    }

    @Test
    void accepted_order_cancellation_notifies_seller_but_pending_cancellation_does_not() {
        User buyer = register("notify_cancel_buyer");
        User seller = register("notify_cancel_seller");
        TradeOrder accepted = newPendingOrder(buyer);
        orderService.accept(accepted.getId(), seller.getId());

        orderService.cancel(accepted.getId(), buyer.getId());

        assertEquals("ORDER_CANCELLED", notificationService.list(seller.getId()).get(0).getType());
        User pendingBuyer = register("notify_pending_buyer");
        TradeOrder pending = newPendingOrder(pendingBuyer);
        orderService.cancel(pending.getId(), pendingBuyer.getId());
        assertTrue(notificationService.list(pendingBuyer.getId()).isEmpty());
    }

    @Test
    void only_recipient_can_mark_notification_read() {
        User recipient = register("notify_read_recipient");
        User other = register("notify_read_other");
        notificationService.push(recipient.getId(), "ORDER_ACCEPTED", "测试通知", 123L);
        Notification notification = notificationService.list(recipient.getId()).get(0);

        assertThrows(BizException.class,
                () -> notificationService.markRead(other.getId(), notification.getId()));
        assertFalse(notificationService.list(recipient.getId()).get(0).isRead());

        notificationService.markRead(recipient.getId(), notification.getId());
        assertTrue(notificationService.list(recipient.getId()).get(0).isRead());
        assertTrue(notificationService.list(other.getId()).isEmpty());
    }

    @Test
    void notification_endpoints_use_current_user_identity() {
        User recipient = register("notify_http_recipient");
        notificationService.push(recipient.getId(), "ORDER_DELIVERED", "接口通知", 456L);
        Notification saved = notificationService.list(recipient.getId()).get(0);
        HttpEntity<Void> request = new HttpEntity<>(authHeaders(recipient));

        ResponseEntity<Map> list = rest.exchange(url("/api/notify"), HttpMethod.GET, request, Map.class);
        assertEquals(0, ((Number) list.getBody().get("code")).intValue());
        List<Map<String, Object>> data = (List<Map<String, Object>>) list.getBody().get("data");
        assertEquals(1, data.size());
        assertEquals(saved.getId(), ((Number) data.get(0).get("id")).longValue());

        ResponseEntity<Map> read = rest.exchange(url("/api/notify/" + saved.getId() + "/read"),
                HttpMethod.POST, request, Map.class);
        assertEquals(0, ((Number) read.getBody().get("code")).intValue());
        assertTrue(notificationService.list(recipient.getId()).get(0).isRead());
    }

    @Test
    void service_purchase_notifies_seller_with_order_reference() {
        User seller = register("notify_service_seller");
        User buyer = register("notify_service_buyer");
        walletService.recharge(buyer.getId(), new BigDecimal("100.00"));
        ServicePublishReq request = new ServicePublishReq();
        request.setTitle("通知测试服务");
        request.setDescription("下单后通知帖主");
        request.setPrice(new BigDecimal("10.00"));
        request.setCategory("学习");
        TradePost service = postService.publishService(seller.getId(), request);

        TradeOrder order = orderService.buy(service.getId(), buyer.getId());

        List<Notification> notifications = notificationService.list(seller.getId());
        assertEquals(1, notifications.size());
        assertEquals("ORDER_CREATED", notifications.get(0).getType());
        assertEquals(order.getId(), notifications.get(0).getRefId());
    }

    private TradeOrder newPendingOrder(User buyer) {
        walletService.recharge(buyer.getId(), new BigDecimal("100.00"));
        PublishReq request = new PublishReq();
        request.setTitle("通知测试悬赏");
        request.setDescription("通知流程测试描述");
        request.setPrice(new BigDecimal("10.00"));
        request.setCategory("跑腿");
        request.setDeadline(LocalDateTime.now().plusDays(1));
        TradePost post = postService.publish(buyer.getId(), request);
        return orderRepository.findByPostIdAndStatusNot(post.getId(), OrderStatus.CANCELLED).orElseThrow();
    }

    private User register(String username) {
        return userService.register(username, "123456", "123456", "通知用户");
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
