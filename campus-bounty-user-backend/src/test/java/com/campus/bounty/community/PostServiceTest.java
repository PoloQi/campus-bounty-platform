package com.campus.bounty.community;

import com.campus.bounty.common.BizException;
import com.campus.bounty.community.dto.CreatePostReq;
import com.campus.bounty.community.dto.PostResp;
import com.campus.bounty.community.dto.UpdatePostReq;
import com.campus.bounty.config.JwtUtil;
import com.campus.bounty.user.User;
import com.campus.bounty.user.UserService;
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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class PostServiceTest {
    @LocalServerPort
    int port;
    @Autowired
    TestRestTemplate rest;
    @Autowired
    PostService postService;
    @Autowired
    PostRepository postRepository;
    @Autowired
    UserService userService;
    @Autowired
    JwtUtil jwtUtil;

    @Test
    void feed_returns_newest_posts_first_with_author_and_content() {
        User author = register("community_feed_author");
        Post older = postService.create(author.getId(), createReq("第一条", "/uploads/old.png", "日常"));
        Post newer = postService.create(author.getId(), createReq("考研组队", "/uploads/new.png", "考研,数学"));

        List<PostResp> feed = postService.feed(null);

        assertTrue(indexOf(feed, newer.getId()) < indexOf(feed, older.getId()));
        PostResp response = feed.get(indexOf(feed, newer.getId()));
        assertEquals("考研组队", response.getContent());
        assertEquals("/uploads/new.png", response.getImages());
        assertEquals("考研,数学", response.getTags());
        assertEquals(author.getNickname(), response.getAuthorNickname());
        assertEquals(0, response.getLikeCount());
        assertEquals("VISIBLE", response.getStatus());
    }

    @Test
    void tag_filter_returns_only_matching_visible_posts() {
        User author = register("community_tag_author");
        Post matching = postService.create(author.getId(), createReq("寻找研友", null, "考研,自习"));
        Post unrelated = postService.create(author.getId(), createReq("周末打球", null, "运动"));

        List<PostResp> result = postService.feed("考研");

        assertTrue(containsId(result, matching.getId()));
        assertFalse(containsId(result, unrelated.getId()));
    }

    @Test
    void only_author_can_update_visible_post() {
        User author = register("community_update_author");
        User outsider = register("community_update_outsider");
        Post post = postService.create(author.getId(), createReq("原内容", null, "日常"));
        UpdatePostReq update = updateReq("新内容", "/uploads/edited.png", "考研");

        PostResp updated = postService.update(post.getId(), author.getId(), update);

        assertEquals("新内容", updated.getContent());
        assertEquals("/uploads/edited.png", updated.getImages());
        assertEquals("考研", updated.getTags());
        assertThrows(BizException.class, () -> postService.update(post.getId(), outsider.getId(), update));
    }

    @Test
    void deleted_post_disappears_from_feed_but_remains_in_mine_over_http() {
        User author = register("community_delete_author");
        Post post = postService.create(author.getId(), createReq("待删除", null, "考研"));

        postService.delete(post.getId(), author.getId());

        assertEquals("DELETED", postRepository.findById(post.getId()).orElseThrow().getStatus());
        assertFalse(containsId(postService.feed(null), post.getId()));
        assertFalse(containsId(postService.feed("考研"), post.getId()));
        assertTrue(postService.mine(author.getId()).stream()
                .anyMatch(item -> item.getId().equals(post.getId()) && item.getStatus().equals("DELETED")));

        ResponseEntity<Map> mine = rest.exchange(url("/api/community/posts/mine"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(author)), Map.class);
        assertEquals(HttpStatus.OK, mine.getStatusCode());
        assertEquals(0, code(mine));
        List<Map<String, Object>> data = (List<Map<String, Object>>) mine.getBody().get("data");
        assertTrue(data.stream().anyMatch(item -> ((Number) item.get("id")).longValue() == post.getId()
                && item.get("status").equals("DELETED")));
    }

    @Test
    void post_without_images_or_tags_can_be_created() {
        User author = register("community_plain_author");

        Post post = postService.create(author.getId(), createReq("纯文字动态", null, null));

        assertNull(post.getImages());
        assertNull(post.getTags());
        assertEquals("VISIBLE", post.getStatus());
    }

    @Test
    void unauthenticated_create_returns_401() {
        ResponseEntity<Map> response = rest.postForEntity(url("/api/community/posts"),
                new HttpEntity<>(createReq("未登录", null, null)), Map.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    private CreatePostReq createReq(String content, String images, String tags) {
        CreatePostReq request = new CreatePostReq();
        request.setContent(content);
        request.setImages(images);
        request.setTags(tags);
        return request;
    }

    private UpdatePostReq updateReq(String content, String images, String tags) {
        UpdatePostReq request = new UpdatePostReq();
        request.setContent(content);
        request.setImages(images);
        request.setTags(tags);
        return request;
    }

    private User register(String username) {
        return userService.register(username, "123456", "123456", "社区作者");
    }

    private boolean containsId(List<PostResp> posts, Long id) {
        return indexOf(posts, id) >= 0;
    }

    private int indexOf(List<PostResp> posts, Long id) {
        for (int i = 0; i < posts.size(); i++) {
            if (posts.get(i).getId().equals(id)) {
                return i;
            }
        }
        return -1;
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
