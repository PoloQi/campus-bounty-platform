package com.campus.bounty.search;

import com.campus.bounty.community.Post;
import com.campus.bounty.community.PostService;
import com.campus.bounty.community.dto.CreatePostReq;
import com.campus.bounty.config.JwtUtil;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class SearchServiceTest {
    @LocalServerPort
    int port;
    @Autowired
    TestRestTemplate rest;
    @Autowired
    SearchService searchService;
    @Autowired
    PostService postService;
    @Autowired
    TradePostService tradePostService;
    @Autowired
    UserService userService;
    @Autowired
    WalletService walletService;
    @Autowired
    JwtUtil jwtUtil;

    @Test
    void search_matches_visible_community_and_open_trade_posts() {
        User author = register("search_domain_author");
        walletService.recharge(author.getId(), new BigDecimal("100.00"));
        String keyword = "search-domain-task9";
        Post community = createPost(author, "社区 " + keyword);
        Post deleted = createPost(author, "删除帖 " + keyword);
        postService.delete(deleted.getId(), author.getId());
        createPost(author, "不相关社区帖");
        TradePost bounty = tradePostService.publish(author.getId(), bountyRequest(keyword));
        TradePost service = tradePostService.publishService(author.getId(), serviceRequest(keyword));
        tradePostService.publishService(author.getId(), serviceRequest("other-title"));

        Map<String, Object> result = searchService.search(keyword);
        List<Post> posts = (List<Post>) result.get("posts");
        List<TradePost> tradePosts = (List<TradePost>) result.get("tradePosts");

        assertEquals(List.of(community.getId()), posts.stream().map(Post::getId).toList());
        assertEquals(List.of(service.getId(), bounty.getId()),
                tradePosts.stream().map(TradePost::getId).toList());
    }

    @Test
    void authenticated_search_endpoint_returns_both_groups() {
        User author = register("search_http_author");
        String keyword = "search-http-task9";
        createPost(author, "接口 " + keyword);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtUtil.generate(author.getId(), author.getUsername()));

        ResponseEntity<Map> response = rest.exchange(url("/api/search?keyword=" + keyword),
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        assertEquals(0, ((Number) response.getBody().get("code")).intValue());
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertTrue(data.containsKey("posts"));
        assertTrue(data.containsKey("tradePosts"));
        assertEquals(1, ((List<?>) data.get("posts")).size());
    }

    private Post createPost(User author, String content) {
        CreatePostReq request = new CreatePostReq();
        request.setContent(content);
        return postService.create(author.getId(), request);
    }

    private PublishReq bountyRequest(String title) {
        PublishReq request = new PublishReq();
        request.setTitle(title);
        request.setDescription("搜索悬赏描述");
        request.setPrice(new BigDecimal("5.00"));
        request.setCategory("跑腿");
        request.setDeadline(LocalDateTime.now().plusDays(1));
        return request;
    }

    private ServicePublishReq serviceRequest(String title) {
        ServicePublishReq request = new ServicePublishReq();
        request.setTitle(title);
        request.setDescription("搜索服务描述");
        request.setPrice(new BigDecimal("8.00"));
        request.setCategory("学习");
        return request;
    }

    private User register(String username) {
        return userService.register(username, "123456", "123456", "搜索用户");
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
