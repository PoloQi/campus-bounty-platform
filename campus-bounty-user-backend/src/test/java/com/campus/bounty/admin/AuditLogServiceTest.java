package com.campus.bounty.admin;

import com.campus.bounty.community.Post;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class AuditLogServiceTest {
    /** 合成管理员 ID：record 不校验管理员存在，用独立 ID 可把断言与其它测试类的历史日志隔离开。 */
    private static final long SYNTHETIC_ADMIN_1 = 900001L;
    private static final long SYNTHETIC_ADMIN_2 = 900002L;
    private static final long SYNTHETIC_ADMIN_3 = 900003L;

    @LocalServerPort
    int port;
    @Autowired
    TestRestTemplate rest;
    @Autowired
    AuditLogService auditLogService;
    @Autowired
    AdminService adminService;
    @Autowired
    PostService postService;
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
    void record_persists_and_query_filters_by_admin_action_and_time() {
        auditLogService.record(SYNTHETIC_ADMIN_1, AuditAction.BAN_USER, "USER", 11L, "封禁用户：a");
        auditLogService.record(SYNTHETIC_ADMIN_1, AuditAction.UNBAN_USER, "USER", 11L, "解封用户：a");
        auditLogService.record(SYNTHETIC_ADMIN_2, AuditAction.BAN_USER, "USER", 12L, "封禁用户：b");

        List<AdminLog> mine = auditLogService.query(SYNTHETIC_ADMIN_1, null, null, null);
        assertEquals(2, mine.size());
        assertEquals(AuditAction.UNBAN_USER, mine.get(0).getAction());
        assertTrue(mine.get(0).getId() > mine.get(1).getId(), "应按 id 倒序返回");

        List<AdminLog> onlyBan = auditLogService.query(SYNTHETIC_ADMIN_1, AuditAction.BAN_USER, null, null);
        assertEquals(1, onlyBan.size());
        assertEquals(11L, onlyBan.get(0).getTargetId());

        List<AdminLog> others = auditLogService.query(SYNTHETIC_ADMIN_2, null, null, null);
        assertEquals(1, others.size());
        assertEquals(12L, others.get(0).getTargetId());

        assertTrue(auditLogService
                .query(SYNTHETIC_ADMIN_1, null, LocalDateTime.now().plusDays(1), null).isEmpty(),
                "时间下界在未来时应查不到");
        assertTrue(auditLogService
                .query(SYNTHETIC_ADMIN_1, null, null, LocalDateTime.now().minusDays(1)).isEmpty(),
                "时间上界在过去时应查不到");
    }

    @Test
    void detail_over_500_is_truncated() {
        String tooLong = "审".repeat(600);

        auditLogService.record(SYNTHETIC_ADMIN_3, AuditAction.HANDLE_REPORT, "REPORT", 21L, tooLong);

        AdminLog saved = auditLogService.query(SYNTHETIC_ADMIN_3, AuditAction.HANDLE_REPORT, null, null).get(0);
        assertEquals(500, saved.getDetail().length());
        assertTrue(saved.getDetail().endsWith("..."));
    }

    @Test
    void delete_post_writes_audit() {
        User admin = admin();
        User author = register("audit_author_1");
        Post post = createPost(author, "待删除的违规帖");

        adminService.deletePost(admin.getId(), post.getId());

        AdminLog log = auditLogService.query(admin.getId(), AuditAction.DELETE_POST, null, null).stream()
                .filter(item -> post.getId().equals(item.getTargetId()))
                .findFirst().orElseThrow();
        assertEquals("POST", log.getTargetType());
        assertTrue(log.getDetail().contains("authorId=" + author.getId()));
        assertNotNull(log.getCreatedAt());
    }

    @Test
    void close_trade_post_and_ban_user_write_audit() {
        User admin = admin();
        User publisher = register("audit_publisher_1");
        TradePost service = tradePostService.publishService(publisher.getId(), serviceRequest());
        User target = register("audit_target_1");

        adminService.closeTradePost(admin.getId(), service.getId());
        adminService.banUser(admin.getId(), target.getId());

        AdminLog closeLog = auditLogService.query(admin.getId(), AuditAction.CLOSE_TRADE_POST, null, null).stream()
                .filter(item -> service.getId().equals(item.getTargetId()))
                .findFirst().orElseThrow();
        assertEquals("TRADE_POST", closeLog.getTargetType());
        assertTrue(closeLog.getDetail().contains("SERVICE"));

        AdminLog banLog = auditLogService.query(admin.getId(), AuditAction.BAN_USER, null, null).stream()
                .filter(item -> target.getId().equals(item.getTargetId()))
                .findFirst().orElseThrow();
        assertEquals("USER", banLog.getTargetType());
        assertTrue(banLog.getDetail().contains(target.getUsername()));
        assertEquals(PostStatus.CLOSED, tradePostRepository.findById(service.getId())
                .orElseThrow().getStatus());
    }

    @Test
    void http_logs_requires_admin() {
        User student = register("audit_student_1");

        ResponseEntity<Map> denied = rest.exchange(url("/api/admin/logs"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(student)), Map.class);
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
        assertEquals(40300, ((Number) denied.getBody().get("code")).intValue());

        ResponseEntity<Map> allowed = rest.exchange(url("/api/admin/logs"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(admin())), Map.class);
        assertEquals(HttpStatus.OK, allowed.getStatusCode());
        assertEquals(0, ((Number) allowed.getBody().get("code")).intValue());
        assertTrue(allowed.getBody().get("data") instanceof List);
    }

    @Test
    void http_logs_filters_by_action() {
        User admin = admin();
        User target = register("audit_target_2");
        adminService.banUser(admin.getId(), target.getId());

        ResponseEntity<Map> response = rest.exchange(url("/api/admin/logs?action=BAN_USER"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(admin)), Map.class);

        assertEquals(0, ((Number) response.getBody().get("code")).intValue());
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
        assertTrue(data.size() >= 1);
        assertTrue(data.stream().allMatch(item -> AuditAction.BAN_USER.equals(item.get("action"))));
        assertTrue(data.stream().anyMatch(item -> target.getId().equals(
                ((Number) item.get("targetId")).longValue())));
    }

    private User admin() {
        return userRepository.findByUsername("admin").orElseThrow();
    }

    private User register(String username) {
        return userService.register(username, "123456", "123456", "审计测试");
    }

    private Post createPost(User author, String content) {
        CreatePostReq request = new CreatePostReq();
        request.setContent(content);
        return postService.create(author.getId(), request);
    }

    private ServicePublishReq serviceRequest() {
        ServicePublishReq request = new ServicePublishReq();
        request.setTitle("待下架服务");
        request.setDescription("审计埋点测试");
        request.setPrice(new BigDecimal("10.00"));
        request.setCategory("学习");
        return request;
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
