package com.campus.bounty.trade;

import com.campus.bounty.common.BizException;
import com.campus.bounty.config.JwtUtil;
import com.campus.bounty.trade.dto.PublishReq;
import com.campus.bounty.user.User;
import com.campus.bounty.user.UserService;
import com.campus.bounty.wallet.Wallet;
import com.campus.bounty.wallet.WalletLog;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class PublishBountyTest {
    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired TradePostService tradePostService;
    @Autowired TradePostRepository postRepository;
    @Autowired TradeOrderRepository orderRepository;
    @Autowired WalletService walletService;
    @Autowired UserService userService;
    @Autowired JwtUtil jwtUtil;

    @Test
    void publish_creates_open_post_pending_order_and_linked_freeze_log() {
        User user = register("publish_bounty_success");
        walletService.recharge(user.getId(), new BigDecimal("100.00"));

        TradePost post = tradePostService.publish(user.getId(), request("20.00"));

        assertEquals(PostType.BOUNTY, post.getType());
        assertEquals(PostStatus.OPEN, post.getStatus());
        assertEquals(user.getId(), post.getPublisherId());
        TradeOrder order = orderRepository.findByPostIdAndStatusNot(post.getId(), OrderStatus.CANCELLED)
                .orElseThrow();
        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertEquals(user.getId(), order.getBuyerId());
        assertNull(order.getSellerId());
        assertMoney("20.00", order.getAmount());

        Wallet wallet = walletService.getOrCreate(user.getId());
        assertMoney("80.00", wallet.getBalance());
        assertMoney("20.00", wallet.getFrozen());
        WalletLog freeze = walletService.logs(user.getId()).get(0);
        assertEquals("FREEZE", freeze.getType());
        assertEquals(order.getId(), freeze.getOrderId());
        assertMoney("-20.00", freeze.getAmount());
    }

    @Test
    void publish_rejects_insufficient_balance_without_post_order_or_freeze() {
        User user = register("publish_bounty_insufficient");
        walletService.recharge(user.getId(), new BigDecimal("5.00"));
        long postCount = postRepository.count();
        long orderCount = orderRepository.count();

        assertThrows(BizException.class,
                () -> tradePostService.publish(user.getId(), request("20.00")));

        assertEquals(postCount, postRepository.count());
        assertEquals(orderCount, orderRepository.count());
        Wallet wallet = walletService.getOrCreate(user.getId());
        assertMoney("5.00", wallet.getBalance());
        assertMoney("0", wallet.getFrozen());
        assertEquals(1, walletService.logs(user.getId()).size());
    }

    @Test
    void authenticated_user_can_publish_over_http() {
        User user = register("publish_bounty_http");
        walletService.recharge(user.getId(), new BigDecimal("30.00"));
        HttpHeaders headers = authHeaders(user);

        ResponseEntity<Map> response = rest.postForEntity(url("/api/trade/posts"),
                new HttpEntity<>(request("12.50"), headers), Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, code(response));
        assertNotNull(response.getBody().get("data"));
        long postId = ((Number) response.getBody().get("data")).longValue();
        assertEquals(user.getId(), postRepository.findById(postId).orElseThrow().getPublisherId());
        assertMoney("17.50", walletService.getOrCreate(user.getId()).getBalance());
        assertMoney("12.50", walletService.getOrCreate(user.getId()).getFrozen());
    }

    @Test
    void publish_http_rejects_missing_required_fields_and_missing_token() {
        User user = register("publish_bounty_validation");
        PublishReq invalid = request("1.00");
        invalid.setCategory(null);

        ResponseEntity<Map> invalidResponse = rest.postForEntity(url("/api/trade/posts"),
                new HttpEntity<>(invalid, authHeaders(user)), Map.class);
        assertEquals(HttpStatus.OK, invalidResponse.getStatusCode());
        assertEquals(40000, code(invalidResponse));

        ResponseEntity<Map> unauthorized = rest.postForEntity(url("/api/trade/posts"),
                request("1.00"), Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, unauthorized.getStatusCode());
        assertEquals(40100, code(unauthorized));
    }

    private PublishReq request(String price) {
        PublishReq request = new PublishReq();
        request.setTitle("帮取快递");
        request.setDescription("菜鸟驿站取件后送到宿舍");
        request.setPrice(new BigDecimal(price));
        request.setCategory("跑腿");
        request.setImages("[\"parcel.jpg\"]");
        request.setDeadline(LocalDateTime.now().plusDays(1));
        return request;
    }

    private User register(String username) {
        return userService.register(username, "123456", "123456", "悬赏测试");
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

    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
