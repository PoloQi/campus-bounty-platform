package com.campus.bounty.admin;

import com.campus.bounty.common.ApiResponse;
import com.campus.bounty.common.BizException;
import com.campus.bounty.community.Post;
import com.campus.bounty.community.PostRepository;
import com.campus.bounty.community.PostService;
import com.campus.bounty.community.dto.CreatePostReq;
import com.campus.bounty.config.JwtUtil;
import com.campus.bounty.trade.PostStatus;
import com.campus.bounty.trade.TradePost;
import com.campus.bounty.trade.TradePostRepository;
import com.campus.bounty.trade.TradePostService;
import com.campus.bounty.trade.dto.ServicePublishReq;
import com.campus.bounty.user.User;
import com.campus.bounty.user.UserRepository;
import com.campus.bounty.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@Import(AdminServiceTest.TestAuthorityController.class)
class AdminServiceTest {
    @LocalServerPort
    int port;
    @Autowired
    TestRestTemplate rest;
    @Autowired
    AdminService adminService;
    @Autowired
    PostService postService;
    @Autowired
    PostRepository postRepository;
    @Autowired
    TradePostService tradePostService;
    @Autowired
    TradePostRepository tradePostRepository;
    @Autowired
    UserService userService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    JwtUtil jwtUtil;

    @Test
    void only_admin_can_delete_community_post() {
        User student = register("admin_delete_student");
        User admin = admin();
        Post post = createPost(student, "违规社区帖");

        BizException denied = assertThrows(BizException.class,
                () -> adminService.deletePost(student.getId(), post.getId()));
        assertEquals(40300, denied.getCode());
        assertEquals("VISIBLE", postRepository.findById(post.getId()).orElseThrow().getStatus());

        adminService.deletePost(admin.getId(), post.getId());
        assertEquals("DELETED", postRepository.findById(post.getId()).orElseThrow().getStatus());
        assertTrue(postService.feed(null).stream().noneMatch(item -> item.getId().equals(post.getId())));
    }

    @Test
    void admin_can_close_service_and_ban_user() {
        User student = register("admin_close_student");
        TradePost service = tradePostService.publishService(student.getId(), serviceRequest());

        adminService.closeTradePost(admin().getId(), service.getId());
        adminService.banUser(admin().getId(), student.getId());

        assertEquals(PostStatus.CLOSED,
                tradePostRepository.findById(service.getId()).orElseThrow().getStatus());
        assertEquals("BANNED", userRepository.findById(student.getId()).orElseThrow().getStatus());
        assertThrows(BizException.class, () -> userService.login(student.getUsername(), "123456"));
    }

    @Test
    void student_can_report_but_only_admin_can_list_pending_reports() {
        User student = register("admin_report_student");

        adminService.report(student.getId(), "POST", 321L, "垃圾广告");

        assertThrows(BizException.class, () -> adminService.pendingReports(student.getId()));
        List<Report> reports = adminService.pendingReports(admin().getId());
        assertTrue(reports.stream().anyMatch(report -> report.getReporterId().equals(student.getId())
                && report.getTargetId().equals(321L) && report.getStatus().equals("PENDING")));
    }

    @Test
    void admin_http_endpoints_use_caller_identity_and_allow_student_reports() {
        User student = register("admin_http_student");
        Post post = createPost(student, "接口删除帖");

        ResponseEntity<Map> denied = rest.exchange(url("/api/admin/posts/" + post.getId() + "/delete"),
                HttpMethod.POST, new HttpEntity<>(authHeaders(student)), Map.class);
        assertEquals(40300, ((Number) denied.getBody().get("code")).intValue());

        ResponseEntity<Map> deleted = rest.exchange(url("/api/admin/posts/" + post.getId() + "/delete"),
                HttpMethod.POST, new HttpEntity<>(authHeaders(admin())), Map.class);
        assertEquals(0, ((Number) deleted.getBody().get("code")).intValue());

        ResponseEntity<Map> reported = rest.postForEntity(url("/api/admin/reports"),
                new HttpEntity<>(Map.of("targetType", "POST", "targetId", post.getId(), "reason", "违规"),
                        authHeaders(student)), Map.class);
        assertEquals(0, ((Number) reported.getBody().get("code")).intValue());
    }

    @Test
    void jwt_authentication_contains_database_role() {
        User student = register("admin_authority_student");

        ResponseEntity<Map> response = rest.exchange(url("/api/test/authorities"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(student)), Map.class);

        assertEquals(List.of("ROLE_STUDENT"), response.getBody().get("data"));
        assertEquals("ADMIN", admin().getRole());
        assertEquals(0, ((Number) loginAdmin().getBody().get("code")).intValue());
    }

    private User admin() {
        return userRepository.findByUsername("admin").orElseThrow();
    }

    private ResponseEntity<Map> loginAdmin() {
        return rest.postForEntity(url("/api/auth/login"),
                Map.of("username", "admin", "password", "admin123"), Map.class);
    }

    private Post createPost(User author, String content) {
        CreatePostReq request = new CreatePostReq();
        request.setContent(content);
        return postService.create(author.getId(), request);
    }

    private ServicePublishReq serviceRequest() {
        ServicePublishReq request = new ServicePublishReq();
        request.setTitle("待下架服务");
        request.setDescription("管理员下架测试");
        request.setPrice(new BigDecimal("10.00"));
        request.setCategory("学习");
        return request;
    }

    private User register(String username) {
        return userService.register(username, "123456", "123456", "治理用户");
    }

    private HttpHeaders authHeaders(User user) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtUtil.generate(user.getId(), user.getUsername()));
        return headers;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @RestController
    static class TestAuthorityController {
        @GetMapping("/api/test/authorities")
        ApiResponse<List<String>> authorities() {
            return ApiResponse.ok(SecurityContextHolder.getContext().getAuthentication()
                    .getAuthorities().stream().map(Object::toString).toList());
        }
    }
}
