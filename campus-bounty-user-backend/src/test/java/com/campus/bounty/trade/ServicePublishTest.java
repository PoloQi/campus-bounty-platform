package com.campus.bounty.trade;

import com.campus.bounty.config.JwtUtil;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class ServicePublishTest {
    @LocalServerPort
    int port;
    @Autowired
    TestRestTemplate rest;
    @Autowired
    TradePostService postService;
    @Autowired
    TradeOrderRepository orderRepository;
    @Autowired
    WalletService walletService;
    @Autowired
    UserService userService;
    @Autowired
    JwtUtil jwtUtil;

    @Test
    void publish_service_creates_open_post_without_order_freeze_or_deadline() {
        User publisher = register("service_publish_domain");
        walletService.recharge(publisher.getId(), new BigDecimal("100.00"));

        TradePost post = postService.publishService(publisher.getId(), serviceRequest("30.00"));

        assertEquals(PostType.SERVICE, post.getType());
        assertEquals(PostStatus.OPEN, post.getStatus());
        assertEquals(publisher.getId(), post.getPublisherId());
        assertNull(post.getDeadline());
        assertTrue(orderRepository.findByPostIdAndStatusNot(post.getId(), OrderStatus.CANCELLED).isEmpty());
        assertEquals(0, walletService.getOrCreate(publisher.getId()).getBalance()
                .compareTo(new BigDecimal("100.00")));
        assertEquals(0, walletService.getOrCreate(publisher.getId()).getFrozen().compareTo(BigDecimal.ZERO));
        assertEquals(1, walletService.logs(publisher.getId()).size());
    }

    @Test
    void authenticated_user_can_publish_service_and_find_it_in_service_hall() {
        User publisher = register("service_publish_http");
        HttpHeaders headers = authHeaders(publisher);

        ResponseEntity<Map> created = rest.postForEntity(url("/api/trade/services"),
                new HttpEntity<>(serviceRequest("18.50"), headers), Map.class);

        assertEquals(HttpStatus.OK, created.getStatusCode());
        assertEquals(0, code(created));
        long postId = ((Number) created.getBody().get("data")).longValue();

        ResponseEntity<Map> hall = rest.exchange(url("/api/trade/posts?type=SERVICE"),
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertEquals(HttpStatus.OK, hall.getStatusCode());
        List<Map<String, Object>> data = (List<Map<String, Object>>) hall.getBody().get("data");
        assertTrue(data.stream().anyMatch(item -> ((Number) item.get("id")).longValue() == postId));
    }

    @Test
    void bounty_publish_still_requires_deadline() {
        User publisher = register("service_publish_bounty_deadline");
        PublishReq request = new PublishReq();
        request.setTitle("悬赏必须有截止时间");
        request.setDescription("验证悬赏原有校验不受服务发布影响");
        request.setPrice(new BigDecimal("5.00"));
        request.setCategory("跑腿");

        ResponseEntity<Map> response = rest.postForEntity(url("/api/trade/posts"),
                new HttpEntity<>(request, authHeaders(publisher)), Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(40000, code(response));
        assertNotNull(response.getBody().get("message"));
    }

    private ServicePublishReq serviceRequest(String price) {
        ServicePublishReq request = new ServicePublishReq();
        request.setTitle("数学家教");
        request.setDescription("大一高数辅导，可线上");
        request.setImages("[\"/uploads/demo.png\"]");
        request.setPrice(new BigDecimal(price));
        request.setCategory("学习");
        return request;
    }

    private User register(String username) {
        return userService.register(username, "123456", "123456", "服务发布测试");
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
}
