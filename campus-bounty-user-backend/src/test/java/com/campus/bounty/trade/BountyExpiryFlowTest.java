package com.campus.bounty.trade;

import com.campus.bounty.common.ApiResponse;
import com.campus.bounty.config.JwtUtil;
import com.campus.bounty.notification.Notification;
import com.campus.bounty.notification.NotificationRepository;
import com.campus.bounty.search.SearchService;
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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.bounty-expiry-scan-ms=86400000",
        "spring.datasource.url=jdbc:h2:mem:bounty_expiry_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class BountyExpiryFlowTest {
    private static final BigDecimal BALANCE = new BigDecimal("100.00");
    private static final BigDecimal REWARD = new BigDecimal("20.00");

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired TradePostService postService;
    @Autowired TradeOrderService orderService;
    @Autowired TradeOrderRepository orders;
    @Autowired TradePostRepository posts;
    @Autowired NotificationRepository notifications;
    @Autowired SearchService search;
    @Autowired WalletService wallet;
    @Autowired UserService users;
    @Autowired JwtUtil jwt;

    @Test
    void pending_bounty_expires_once_per_deadline_and_owner_can_renew_or_cancel() {
        User publisher = register("expiry_publisher");
        User other = register("expiry_other");
        wallet.recharge(publisher.getId(), BALANCE);
        String title = "悬赏到期测试：搬图书";
        TradeOrder order = publish(publisher, title);
        String path = "/api/trade/orders/" + order.getId();
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime renewal = now.plusHours(2);
        assertNotEquals(0, code(post(path + "/extend", publisher, Map.of("deadline", renewal.toString()))));

        changeDeadline(order.getPostId(), now.minusHours(3));
        assertNotEquals(0, code(post(path + "/accept", other, null)));
        assertNotEquals(0, code(post(path + "/extend", other, Map.of("deadline", renewal.toString()))));
        assertNotEquals(0, code(post(path + "/extend", publisher, Map.of("deadline", now.minusHours(1).toString()))));
        assertEquals(OrderStatus.PENDING, orders.findById(order.getId()).orElseThrow().getStatus());
        assertVisibility(order.getPostId(), title, false);
        assertWallet(publisher, BALANCE.subtract(REWARD), REWARD);

        orderService.notifyDeadlineElapsed(order.getId());
        orderService.notifyDeadlineElapsed(order.getId());
        List<Notification> first = expiryNotices(publisher);
        assertEquals(1, first.size());
        assertEquals(order.getId(), first.get(0).getRefId());
        assertFalse(first.get(0).isRead());
        assertTrue(expiryNotices(other).isEmpty());

        assertEquals(0, code(post("/api/notify/" + first.get(0).getId() + "/read", publisher, null)));
        orderService.notifyDeadlineElapsed(order.getId());
        List<Notification> read = expiryNotices(publisher);
        assertEquals(1, read.size());
        assertTrue(read.get(0).isRead());

        var extended = post(path + "/extend", publisher, Map.of("deadline", renewal.toString()));
        assertEquals(0, code(extended));
        assertEquals("PENDING", ((Map<?, ?>) extended.get("data")).get("status"));
        assertEquals(renewal, posts.findById(order.getPostId()).orElseThrow().getDeadline());
        assertWallet(publisher, BALANCE.subtract(REWARD), REWARD);
        assertVisibility(order.getPostId(), title, true);
        orderService.notifyDeadlineElapsed(order.getId());
        assertEquals(1, expiryNotices(publisher).size());

        // Simulate elapsed time relative to the previous notice and the renewed persisted deadline.
        Notification previous = notifications.findById(read.get(0).getId()).orElseThrow();
        previous.setCreatedAt(now.minusHours(2));
        notifications.saveAndFlush(previous);
        changeDeadline(order.getPostId(), now.minusHours(1));
        orderService.notifyDeadlineElapsed(order.getId());
        orderService.notifyDeadlineElapsed(order.getId());
        List<Notification> both = expiryNotices(publisher);
        assertEquals(2, both.size());
        assertFalse(both.get(0).isRead());
        assertEquals(order.getId(), both.get(0).getRefId());
        assertVisibility(order.getPostId(), title, false);

        assertEquals(0, code(post(path + "/cancel", publisher, null)));
        assertEquals(OrderStatus.CANCELLED, orders.findById(order.getId()).orElseThrow().getStatus());
        assertEquals(PostStatus.CLOSED, posts.findById(order.getPostId()).orElseThrow().getStatus());
        assertWallet(publisher, BALANCE, BigDecimal.ZERO);
        orderService.notifyDeadlineElapsed(order.getId());
        assertEquals(2, expiryNotices(publisher).size());
    }

    @Test
    void accepted_bounty_is_not_subject_to_the_acceptance_deadline() {
        User publisher = register("expiry_accept_buyer");
        User seller = register("expiry_accept_seller");
        wallet.recharge(publisher.getId(), BALANCE);
        TradeOrder order = publish(publisher, "悬赏到期测试：已接单");
        orderService.accept(order.getId(), seller.getId());
        changeDeadline(order.getPostId(), LocalDateTime.now().minusHours(1));

        orderService.notifyDeadlineElapsed(order.getId());
        assertTrue(expiryNotices(publisher).isEmpty());
        assertEquals(OrderStatus.IN_PROGRESS, orders.findById(order.getId()).orElseThrow().getStatus());
        orderService.deliver(order.getId(), seller.getId());
        orderService.confirm(order.getId(), publisher.getId());
        assertEquals(OrderStatus.COMPLETED, orders.findById(order.getId()).orElseThrow().getStatus());
        assertWallet(publisher, BALANCE.subtract(REWARD), BigDecimal.ZERO);
        assertWallet(seller, REWARD, BigDecimal.ZERO);
    }

    @Test
    void rejects_a_past_deadline_when_publishing_without_freezing_funds() {
        User publisher = register("expiry_invalid_date");
        wallet.recharge(publisher.getId(), BALANCE);
        var result = post("/api/trade/posts", publisher,
                request("悬赏到期测试：过去截止时间", LocalDateTime.now().minusHours(1)));
        assertNotEquals(0, code(result));
        assertTrue(postService.hallMine(publisher.getId()).isEmpty());
        assertWallet(publisher, BALANCE, BigDecimal.ZERO);
    }

    private User register(String username) {
        return users.register(username, "123456", "123456", "截止时间测试");
    }

    private PublishReq request(String title, LocalDateTime time) {
        var request = new PublishReq();
        request.setTitle(title);
        request.setDescription("截止时间测试用悬赏");
        request.setCategory("跑腿");
        request.setPrice(REWARD);
        request.setDeadline(time);
        return request;
    }

    private TradeOrder publish(User publisher, String title) {
        var post = postService.publish(publisher.getId(), request(title, LocalDateTime.now().plusHours(1)));
        return orders.findByPostId(post.getId()).orElseThrow();
    }

    private void changeDeadline(Long postId, LocalDateTime deadline) {
        TradePost post = posts.findById(postId).orElseThrow();
        post.setDeadline(deadline);
        posts.saveAndFlush(post);
    }

    private Map<?, ?> post(String path, User operator, Object body) {
        return Objects.requireNonNull(rest.postForObject(url(path), new HttpEntity<>(body, auth(operator)), Map.class));
    }

    private int code(Map<?, ?> response) {
        return ((Number) response.get("code")).intValue();
    }

    private List<Notification> expiryNotices(User recipient) {
        var response = rest.exchange(url("/api/notify"), HttpMethod.GET, new HttpEntity<>(auth(recipient)),
                new ParameterizedTypeReference<ApiResponse<List<Notification>>>() {});
        var body = Objects.requireNonNull(response.getBody());
        assertEquals(0, body.getCode());
        return body.getData().stream().filter(notice -> notice.getType().equals("BOUNTY_EXPIRED")).toList();
    }

    private void assertVisibility(Long id, String title, boolean expected) {
        assertEquals(expected, postService.hall(PostType.BOUNTY, null, title).stream()
                .anyMatch(post -> post.getId().equals(id)));
        assertEquals(expected, ((List<?>) search.search(title).get("tradePosts")).stream()
                .anyMatch(post -> ((TradePost) post).getId().equals(id)));
        var response = rest.exchange(url(PublicBountyController.TITLES_PATH), HttpMethod.GET, HttpEntity.EMPTY,
                new ParameterizedTypeReference<ApiResponse<List<String>>>() {});
        var body = Objects.requireNonNull(response.getBody());
        assertEquals(0, body.getCode());
        assertEquals(expected, body.getData().contains(title));
    }

    private void assertWallet(User user, BigDecimal balance, BigDecimal frozen) {
        var state = wallet.getOrCreate(user.getId());
        assertEquals(0, balance.compareTo(state.getBalance()));
        assertEquals(0, frozen.compareTo(state.getFrozen()));
    }

    private HttpHeaders auth(User user) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(jwt.generate(user.getId(), user.getUsername()));
        return headers;
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }
}
