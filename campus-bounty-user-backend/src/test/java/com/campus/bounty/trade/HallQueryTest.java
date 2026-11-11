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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class HallQueryTest {
    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired TradePostService tradePostService;
    @Autowired TradePostRepository postRepository;
    @Autowired WalletService walletService;
    @Autowired UserService userService;
    @Autowired JwtUtil jwtUtil;

    @Test
    void hall_returns_only_open_posts_and_filters_type_category_and_keyword() {
        User user = register("hall_filter_publisher");
        walletService.recharge(user.getId(), new BigDecimal("100.00"));
        TradePost parcel = publish(user, "帮拿快递h8", "跑腿");
        publish(user, "高数辅导h8", "学习");
        TradePost closed = publish(user, "已关闭快递h8", "跑腿");
        closed.setStatus(PostStatus.CLOSED);
        postRepository.save(closed);

        List<TradePost> allBounties = tradePostService.hall(PostType.BOUNTY, null, null);
        assertTrue(allBounties.stream().anyMatch(post -> post.getId().equals(parcel.getId())));
        assertTrue(allBounties.stream().noneMatch(post -> post.getId().equals(closed.getId())));
        assertTrue(allBounties.stream().allMatch(post -> post.getStatus() == PostStatus.OPEN));

        List<TradePost> errands = tradePostService.hall(PostType.BOUNTY, "跑腿", "h8");
        assertTrue(errands.stream().anyMatch(post -> post.getId().equals(parcel.getId())));
        assertTrue(errands.stream().allMatch(post -> post.getCategory().equals("跑腿")));
        assertTrue(tradePostService.hall(PostType.BOUNTY, null, "不存在的关键字").isEmpty());
        assertTrue(tradePostService.hall(PostType.SERVICE, null, "h8").isEmpty());
    }

    @Test
    void detail_returns_post_and_active_order_and_missing_post_is_rejected() {
        User user = register("hall_detail_publisher");
        walletService.recharge(user.getId(), new BigDecimal("20.00"));
        TradePost post = publish(user, "大厅详情h8", "跑腿");

        PostView view = tradePostService.detail(post.getId());

        assertEquals(post.getId(), view.getPost().getId());
        assertNotNull(view.getOrder());
        assertEquals(post.getId(), view.getOrder().getPostId());
        assertEquals(OrderStatus.PENDING, view.getOrder().getStatus());
        assertThrows(BizException.class, () -> tradePostService.detail(-1L));
    }

    @Test
    void authenticated_user_can_query_hall_and_detail_over_http() {
        User user = register("hall_http_publisher");
        walletService.recharge(user.getId(), new BigDecimal("20.00"));
        TradePost post = publish(user, "HTTP大厅h8", "跑腿");
        HttpHeaders headers = authHeaders(user);

        ResponseEntity<Map> hall = exchange("/api/trade/posts?type=BOUNTY&category=跑腿&keyword=HTTP大厅h8",
                headers);
        assertEquals(HttpStatus.OK, hall.getStatusCode());
        assertEquals(0, code(hall));
        List<Map<String, Object>> posts = (List<Map<String, Object>>) hall.getBody().get("data");
        assertEquals(1, posts.size());
        assertEquals(post.getId().longValue(), ((Number) posts.get(0).get("id")).longValue());

        ResponseEntity<Map> detail = exchange("/api/trade/posts/" + post.getId(), headers);
        assertEquals(0, code(detail));
        Map<String, Object> data = (Map<String, Object>) detail.getBody().get("data");
        Map<String, Object> returnedPost = (Map<String, Object>) data.get("post");
        Map<String, Object> returnedOrder = (Map<String, Object>) data.get("order");
        assertEquals(post.getId().longValue(), ((Number) returnedPost.get("id")).longValue());
        assertEquals("PENDING", returnedOrder.get("status"));
    }

    private TradePost publish(User user, String title, String category) {
        PublishReq request = new PublishReq();
        request.setTitle(title);
        request.setDescription("大厅查询测试描述");
        request.setPrice(new BigDecimal("5.00"));
        request.setCategory(category);
        request.setDeadline(LocalDateTime.now().plusDays(1));
        return tradePostService.publish(user.getId(), request);
    }

    private User register(String username) {
        return userService.register(username, "123456", "123456", "大厅测试");
    }

    private HttpHeaders authHeaders(User user) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtUtil.generate(user.getId(), user.getUsername()));
        return headers;
    }

    private ResponseEntity<Map> exchange(String path, HttpHeaders headers) {
        return rest.exchange("http://localhost:" + port + path, HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
    }

    private int code(ResponseEntity<Map> response) {
        return ((Number) response.getBody().get("code")).intValue();
    }
}
