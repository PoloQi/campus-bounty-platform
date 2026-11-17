package com.campus.bounty.admin;

import com.campus.bounty.common.ApiResponse;
import com.campus.bounty.common.BizException;
import com.campus.bounty.community.Post;
import com.campus.bounty.community.PostRepository;
import com.campus.bounty.community.PostService;
import com.campus.bounty.community.dto.CreatePostReq;
import com.campus.bounty.config.JwtUtil;
import com.campus.bounty.trade.PostStatus;
import com.campus.bounty.trade.TradeOrderService;
import com.campus.bounty.trade.TradePost;
import com.campus.bounty.trade.TradePostRepository;
import com.campus.bounty.trade.TradePostService;
import com.campus.bounty.trade.dto.ServicePublishReq;
import com.campus.bounty.user.User;
import com.campus.bounty.user.UserRepository;
import com.campus.bounty.user.UserService;
import com.campus.bounty.wallet.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    @Autowired
    TradeOrderService orders;
    @Autowired
    WalletService wallets;

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
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());

        ResponseEntity<Map> deleted = rest.exchange(url("/api/admin/posts/" + post.getId() + "/delete"),
                HttpMethod.POST, new HttpEntity<>(authHeaders(admin())), Map.class);
        assertEquals(0, ((Number) deleted.getBody().get("code")).intValue());

        ResponseEntity<Map> reported = rest.postForEntity(url("/api/reports"),
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

    @Test
    void demo_admin_is_only_created_for_local_and_test_profiles() {
        Profile profile = AnnotationUtils.findAnnotation(AdminBootstrap.class, Profile.class);

        assertNotNull(profile);
        assertArrayEquals(new String[]{"local", "test"}, profile.value());
    }

    @Test
    void report_decisions_notify_reporter_and_audit_the_linked_action_once() {
        User reporter = register("report_decision_reporter");
        User author = register("report_decision_author");
        User admin = admin();
        Post removed = createPost(author, "确认违规");
        Post kept = createPost(author, "保留内容");
        adminService.report(reporter.getId(), "POST", removed.getId(), "违规");
        adminService.report(reporter.getId(), "POST", kept.getId(), "需要复核");

        List<Map<?, ?>> pending = items(read(admin, "/api/admin/reports/query?status=PENDING&targetType=POST"));
        long valid = reportId(pending, removed.getId());
        long rejected = reportId(pending, kept.getId());
        String validPath = "/api/admin/reports/" + valid + "/handle";
        String rejectedPath = "/api/admin/reports/" + rejected + "/handle";
        assertEquals(0, code(write(admin, validPath, Map.of("result", "VALID", "comment", "确认广告", "moderate", true))));
        assertTrue(code(write(admin, validPath, Map.of("result", "VALID", "comment", "重复"))) != 0);
        assertTrue(code(write(admin, rejectedPath, Map.of("result", "REJECTED", "comment", "无违规", "moderate", true))) != 0);
        assertEquals(0, code(write(admin, rejectedPath, Map.of("result", "REJECTED", "comment", "无违规"))));

        assertEquals("DELETED", postRepository.findById(removed.getId()).orElseThrow().getStatus());
        assertEquals("VISIBLE", postRepository.findById(kept.getId()).orElseThrow().getStatus());
        Map<?, ?> resolved = (Map<?, ?>) read(admin, "/api/admin/reports/" + valid);
        assertEquals("HANDLED", resolved.get("status"));
        assertEquals("DELETE_POST", resolved.get("moderationAction"));
        assertEquals("确认广告", resolved.get("comment"));
        List<Map<?, ?>> notices = (List<Map<?, ?>>) read(reporter, "/api/notify");
        assertEquals(2L, notices.stream().filter(notice -> "REPORT_HANDLED".equals(notice.get("type"))).count());
        assertTrue(notices.stream().anyMatch(notice -> String.valueOf(notice.get("content")).contains("确认广告")));
        assertTrue(notices.stream().anyMatch(notice -> String.valueOf(notice.get("content")).contains("无违规")));
        List<Map<?, ?>> audit = items(read(admin, "/api/admin/audit?adminId=" + admin.getId() + "&action=HANDLE_REPORT"));
        assertEquals(1L, audit.stream().filter(log -> ((Number) log.get("targetId")).longValue() == valid).count());
        assertTrue(audit.stream().anyMatch(log -> ((Number) log.get("targetId")).longValue() == rejected));
    }

    @Test
    void oversight_queries_reconcile_and_explain_a_service_order() {
        User buyer = register("oversight_order_buyer");
        User seller = register("oversight_order_seller");
        User admin = admin();
        TradePost service = tradePostService.publishService(seller.getId(), serviceRequest());
        wallets.recharge(buyer.getId(), new BigDecimal("30.00"));
        Long orderId = orders.buy(service.getId(), buyer.getId()).getId();
        String filter = "/api/admin/orders?type=SERVICE&status=IN_PROGRESS&buyerId=" + buyer.getId()
                + "&sellerId=" + seller.getId() + "&from=" + java.time.LocalDate.now();
        assertEquals(List.of(orderId.longValue()), items(read(admin, filter)).stream()
                .map(row -> ((Number) row.get("id")).longValue()).toList());
        Map<?, ?> dashboard = (Map<?, ?>) read(admin, "/api/admin/dashboard");
        assertEquals(7, ((List<?>) dashboard.get("trend")).size());
        assertEquals(6, ((List<?>) dashboard.get("distribution")).size());
        assertEquals(0, decimal(((Map<?, ?>) read(admin, "/api/admin/funds")).get("difference")).signum());

        orders.deliver(orderId, seller.getId());
        orders.confirm(orderId, buyer.getId());
        Map<?, ?> detail = (Map<?, ?>) read(admin, "/api/admin/orders/" + orderId);
        assertTrue(!((Map<?, ?>) detail.get("buyer")).containsKey("passwordHash"));
        List<Map<?, ?>> timeline = (List<Map<?, ?>>) detail.get("timeline");
        assertTrue(timeline.stream().anyMatch(event -> "ORDER_DELIVERED".equals(event.get("type"))));
        assertTrue(timeline.stream().anyMatch(event -> "ORDER_COMPLETED".equals(event.get("type"))));
        List<Map<?, ?>> ledger = items(read(admin, "/api/admin/funds/ledger?orderId=" + orderId + "&type=PAY"));
        assertEquals(1, ledger.size());
        assertEquals(0, decimal(ledger.get(0).get("amount")).signum());
        assertEquals(0, decimal(((Map<?, ?>) read(admin, "/api/admin/funds")).get("difference")).signum());
        assertEquals(HttpStatus.FORBIDDEN, rest.exchange(url("/api/admin/orders/" + orderId), HttpMethod.GET,
                new HttpEntity<>(authHeaders(seller)), Map.class).getStatusCode());
    }

    @Test
    void admins_can_find_a_banned_user_and_restore_login_without_touching_wallet() {
        User admin = admin();
        User student = register("oversight_ban_student");
        wallets.recharge(student.getId(), new BigDecimal("15.00"));
        adminService.banUser(admin.getId(), student.getId());
        assertEquals(1, items(read(admin, "/api/admin/users?keyword=" + student.getId() + "&status=BANNED")).size());
        assertThrows(BizException.class, () -> userService.login(student.getUsername(), "123456"));
        assertEquals(0, code(write(admin, "/api/admin/users/" + student.getId() + "/unban", Map.of())));
        assertNotNull(userService.login(student.getUsername(), "123456"));
        assertEquals(0, wallets.getOrCreate(student.getId()).getBalance().compareTo(new BigDecimal("15.00")));
        assertTrue(items(read(admin, "/api/admin/audit?action=UNBAN_USER")).stream()
                .anyMatch(log -> ((Number) log.get("targetId")).longValue() == student.getId()));
    }

    private Object read(User user, String path) {
        var response = rest.exchange(url(path), HttpMethod.GET, new HttpEntity<>(authHeaders(user)), Map.class);
        assertEquals(0, code(response), response.toString());
        return response.getBody().get("data");
    }

    private ResponseEntity<Map> write(User user, String path, Map<String, ?> body) {
        return rest.postForEntity(url(path), new HttpEntity<>(body, authHeaders(user)), Map.class);
    }

    private int code(ResponseEntity<Map> response) {
        return ((Number) response.getBody().get("code")).intValue();
    }

    @SuppressWarnings("unchecked")
    private List<Map<?, ?>> items(Object page) {
        return (List<Map<?, ?>>) ((Map<?, ?>) page).get("items");
    }

    private long reportId(List<Map<?, ?>> reports, Long targetId) {
        return reports.stream().filter(row -> ((Number) row.get("targetId")).longValue() == targetId)
                .map(row -> ((Number) row.get("id")).longValue()).findFirst().orElseThrow();
    }

    private BigDecimal decimal(Object value) {
        return new BigDecimal(String.valueOf(value));
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
