package com.campus.bounty.trade;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties =
        "spring.datasource.url=jdbc:h2:mem:public_bounty_titles;MODE=MySQL;DATABASE_TO_LOWER=TRUE")
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class PublicBountyTitlesTest {
    @Autowired TestRestTemplate rest;
    @Autowired TradePostRepository tradePosts;
    @LocalServerPort int port;

    @Test
    void unmappedRouteAndUnsupportedMethodReturnHttpErrors() {
        String origin = "http://127.0.0.1:" + port;
        var missing = rest.getForEntity(origin + "/api/auth/unmapped", Map.class);
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());
        assertEquals(40400, missing.getBody().get("code"));
        assertEquals("请求的资源不存在", missing.getBody().get("message"));

        var method = rest.exchange(origin + "/api/auth/login", HttpMethod.PUT, HttpEntity.EMPTY, Map.class);
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, method.getStatusCode());
        assertEquals(40500, method.getBody().get("code"));
        assertEquals(true, method.getHeaders().getAllow().contains(HttpMethod.POST));
    }

    @Test
    void guestReceivesOnlyOpenBountyTitlesWithoutContactDetails() {
        TradePost valid = post("社团招新海报排版", PostType.BOUNTY, PostStatus.OPEN);
        TradePost older = post("帮打印课程讲义", PostType.BOUNTY, PostStatus.OPEN);
        var inserted = tradePosts.saveAllAndFlush(List.of(
                older, valid,
                post("代取快递 １３８-００００-１２３４", PostType.BOUNTY, PostStatus.OPEN),
                post("帮忙送到宿舍 403", PostType.BOUNTY, PostStatus.OPEN),
                post("小组讨论，微信 contact", PostType.BOUNTY, PostStatus.OPEN),
                post("高等数学辅导", PostType.SERVICE, PostStatus.OPEN),
                post("已下架的悬赏", PostType.BOUNTY, PostStatus.CLOSED)));
        String origin = "http://127.0.0.1:" + port;
        try {
            var response = rest.getForEntity(origin + PublicBountyController.TITLES_PATH, Map.class);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(List.of(valid.getTitle(), older.getTitle()), response.getBody().get("data"));
            assertEquals(HttpStatus.UNAUTHORIZED,
                    rest.getForEntity(origin + "/api/trade/posts", Map.class).getStatusCode());
        } finally {
            tradePosts.deleteAll(inserted);
        }
    }

    private TradePost post(String title, PostType type, PostStatus status) {
        TradePost post = new TradePost();
        post.setPublisherId(1L);
        post.setTitle(title);
        post.setDescription("仅登录后可查看的任务描述");
        post.setPrice(BigDecimal.ONE);
        post.setCategory("设计制作");
        post.setType(type);
        post.setStatus(status);
        return post;
    }
}
