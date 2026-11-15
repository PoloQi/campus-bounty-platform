package com.campus.bounty.admin;

import com.campus.bounty.common.ApiResponse;
import com.campus.bounty.common.BizException;
import com.campus.bounty.config.JwtUtil;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B5 用户治理 · 解封验收。
 * <p>
 * 除「状态回到 ACTIVE 且能重新登录」外，重点锁定两个容易出错的语义：
 * 1) 解封后**旧 token 自动恢复可用**（{@code JwtAuthFilter} 每请求查库，BANNED 只是当时跳过写 SecurityContext，
 *    token 本身没有被吊销）——这是当前实现的真实行为，必须由测试固定下来，否则将来有人改成「token 黑名单」会静默改变接口语义。
 * 2) 一次解封只留**一条** UNBAN_USER 审计，且 detail 里带得出用户名。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@Import(AdminUnbanTest.TestProbeController.class)
@SuppressWarnings("unchecked")
class AdminUnbanTest {
    @LocalServerPort
    int port;
    @Autowired
    TestRestTemplate rest;
    @Autowired
    AdminService adminService;
    @Autowired
    AuditLogService auditLogService;
    @Autowired
    UserService userService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    JwtUtil jwtUtil;

    @Test
    void ban_then_unban_restores_active_status_and_login() {
        User student = register("unb_login");

        adminService.banUser(admin().getId(), student.getId());
        assertEquals("BANNED", status(student.getId()));
        assertThrows(BizException.class, () -> userService.login(student.getUsername(), "123456"));

        adminService.unbanUser(admin().getId(), student.getId());

        assertEquals("ACTIVE", status(student.getId()));
        User loggedIn = userService.login(student.getUsername(), "123456");
        assertEquals(student.getId(), loggedIn.getId());
    }

    @Test
    void old_token_becomes_usable_again_after_unban() {
        User student = register("unb_token");
        HttpHeaders headers = authHeaders(student);

        // 解封前先确认这个 token 本来是好用的，避免把「token 失效」误判成「封禁生效」
        assertEquals(HttpStatus.OK, probe(headers).getStatusCode());

        adminService.banUser(admin().getId(), student.getId());
        student = userRepository.findById(student.getId()).orElseThrow();
        assertEquals("BANNED", status(student.getId()));
        assertEquals(HttpStatus.UNAUTHORIZED, probe(authHeaders(student)).getStatusCode(),
                "封禁后应立刻 401");

        adminService.unbanUser(admin().getId(), student.getId());
        assertEquals(HttpStatus.OK, probe(authHeaders(student)).getStatusCode(),
                "解封后旧 token 应恢复可用（JwtAuthFilter 每请求查库，不维护 token 黑名单）");
    }

    @Test
    void unban_writes_exactly_one_audit_with_username() {
        User student = register("unb_audit");
        adminService.banUser(admin().getId(), student.getId());
        adminService.unbanUser(admin().getId(), student.getId());

        List<AdminLog> audits = unbanAudits(student.getId());
        assertEquals(1, audits.size(), "一次解封只应留一条 UNBAN_USER 审计");
        AdminLog log = audits.get(0);
        assertEquals(AuditAction.UNBAN_USER, log.getAction());
        assertEquals("USER", log.getTargetType());
        assertEquals(admin().getId(), log.getAdminId());
        assertNotNull(log.getDetail());
        assertTrue(log.getDetail().contains(student.getUsername()), log.getDetail());
    }

    @Test
    void unban_is_idempotent_on_already_active_user() {
        User student = register("unb_idem");
        assertEquals("ACTIVE", status(student.getId()));

        // 不抛异常，状态保持 ACTIVE；每次调用都会被记录（管理员确实点了，审计要能追溯）
        adminService.unbanUser(admin().getId(), student.getId());
        adminService.unbanUser(admin().getId(), student.getId());

        assertEquals("ACTIVE", status(student.getId()));
        assertEquals(2, unbanAudits(student.getId()).size());
    }

    @Test
    void student_cannot_unban_and_unknown_user_returns_biz_error() {
        User student = register("unb_student");

        ResponseEntity<Map> denied = rest.exchange(url("/api/admin/users/" + student.getId() + "/unban"),
                HttpMethod.POST, new HttpEntity<>(authHeaders(student)), Map.class);
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode(), "学生 token 必须被 hasRole(ADMIN) 拦下");

        BizException serviceDenied = assertThrows(BizException.class,
                () -> adminService.unbanUser(student.getId(), student.getId()));
        assertEquals(40300, serviceDenied.getCode());

        // 未登录
        ResponseEntity<Map> anonymous = rest.exchange(url("/api/admin/users/" + student.getId() + "/unban"),
                HttpMethod.POST, HttpEntity.EMPTY, Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, anonymous.getStatusCode());

        // 用户不存在：业务错误走 200 + 40000
        ResponseEntity<Map> notFound = rest.exchange(url("/api/admin/users/99999999/unban"),
                HttpMethod.POST, new HttpEntity<>(authHeaders(admin())), Map.class);
        assertEquals(HttpStatus.OK, notFound.getStatusCode());
        assertEquals(40000, ((Number) notFound.getBody().get("code")).intValue());

        // 解封失败不能伪造审计
        assertTrue(auditLogService.query(admin().getId(), AuditAction.UNBAN_USER, null, null).stream()
                        .noneMatch(log -> Long.valueOf(99999999L).equals(log.getTargetId())),
                "用户不存在时不应留下 UNBAN_USER 审计");
    }

    private ResponseEntity<Map> probe(HttpHeaders headers) {
        return rest.exchange(url("/api/test/probe"), HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
    }

    private List<AdminLog> unbanAudits(Long userId) {
        return auditLogService.query(null, AuditAction.UNBAN_USER, null, null).stream()
                .filter(log -> userId.equals(log.getTargetId()))
                .toList();
    }

    private String status(Long userId) {
        return userRepository.findById(userId).orElseThrow().getStatus();
    }

    private User admin() {
        return userRepository.findByUsername("admin").orElseThrow();
    }

    private User register(String username) {
        return userService.register(username, "123456", "123456", "解封测试用户");
    }

    private HttpHeaders authHeaders(User user) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtUtil.generate(user.getId(), user.getUsername()));
        return headers;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    /** 用一个需要登录的最小接口探活，避免依赖 /api/wallet 等业务接口的额外校验。 */
    @RestController
    static class TestProbeController {
        @GetMapping("/api/test/probe")
        ApiResponse<String> probe() {
            return ApiResponse.ok("ok");
        }
    }
}
