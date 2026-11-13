package com.campus.bounty.review;

import com.campus.bounty.common.BizException;
import com.campus.bounty.config.JwtUtil;
import com.campus.bounty.trade.OrderStatus;
import com.campus.bounty.trade.TradeOrder;
import com.campus.bounty.trade.TradeOrderRepository;
import com.campus.bounty.trade.TradeOrderService;
import com.campus.bounty.trade.TradePost;
import com.campus.bounty.trade.TradePostService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class ReviewServiceTest {
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
    ReviewService reviewService;
    @Autowired
    JwtUtil jwtUtil;

    @Test
    void incomplete_order_and_non_participant_cannot_review() {
        User buyer = register("review_status_buyer");
        User seller = register("review_status_seller");
        User outsider = register("review_status_outsider");
        TradeOrder order = newPendingOrder(buyer);

        assertThrows(BizException.class, () -> reviewService.review(order.getId(), buyer.getId(), "太早"));
        complete(order, seller);
        assertThrows(BizException.class, () -> reviewService.review(order.getId(), outsider.getId(), "路人"));
    }

    @Test
    void each_participant_can_review_once_and_recipient_sees_it() {
        User buyer = register("review_once_buyer");
        User seller = register("review_once_seller");
        TradeOrder order = newPendingOrder(buyer);
        complete(order, seller);

        assertThrows(BizException.class, () -> reviewService.review(order.getId(), buyer.getId(), " "));
        Review buyerReview = reviewService.review(order.getId(), buyer.getId(), "交付很好");
        Review sellerReview = reviewService.review(order.getId(), seller.getId(), "沟通顺畅");

        assertEquals(seller.getId(), buyerReview.getToUserId());
        assertEquals(buyer.getId(), sellerReview.getToUserId());
        assertEquals(List.of(buyerReview.getId()),
                reviewService.ofUser(seller.getId()).stream().map(Review::getId).toList());
        assertEquals(List.of(sellerReview.getId()),
                reviewService.ofUser(buyer.getId()).stream().map(Review::getId).toList());
        assertThrows(BizException.class, () -> reviewService.review(order.getId(), buyer.getId(), "重复评价"));
    }

    @Test
    void authenticated_review_endpoints_create_and_list_review() {
        User buyer = register("review_http_buyer");
        User seller = register("review_http_seller");
        TradeOrder order = newPendingOrder(buyer);
        complete(order, seller);
        HttpEntity<Map<String, Object>> createRequest = new HttpEntity<>(
                Map.of("orderId", order.getId(), "content", "HTTP评价"), authHeaders(buyer));

        ResponseEntity<Map> created = rest.postForEntity(url("/api/reviews"), createRequest, Map.class);
        assertEquals(0, ((Number) created.getBody().get("code")).intValue());
        long reviewId = ((Number) created.getBody().get("data")).longValue();

        ResponseEntity<Map> list = rest.exchange(url("/api/reviews/user/" + seller.getId()),
                HttpMethod.GET, new HttpEntity<>(authHeaders(buyer)), Map.class);
        List<Map<String, Object>> data = (List<Map<String, Object>>) list.getBody().get("data");
        assertEquals(1, data.size());
        assertEquals(reviewId, ((Number) data.get(0).get("id")).longValue());
        assertEquals("HTTP评价", data.get(0).get("content"));
    }

    private TradeOrder newPendingOrder(User buyer) {
        walletService.recharge(buyer.getId(), new BigDecimal("100.00"));
        PublishReq request = new PublishReq();
        request.setTitle("评价测试悬赏");
        request.setDescription("评价流程测试描述");
        request.setPrice(new BigDecimal("10.00"));
        request.setCategory("跑腿");
        request.setDeadline(LocalDateTime.now().plusDays(1));
        TradePost post = postService.publish(buyer.getId(), request);
        return orderRepository.findByPostIdAndStatusNot(post.getId(), OrderStatus.CANCELLED).orElseThrow();
    }

    private void complete(TradeOrder order, User seller) {
        orderService.accept(order.getId(), seller.getId());
        orderService.deliver(order.getId(), seller.getId());
        orderService.confirm(order.getId(), order.getBuyerId());
    }

    private User register(String username) {
        return userService.register(username, "123456", "123456", "评价用户");
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
