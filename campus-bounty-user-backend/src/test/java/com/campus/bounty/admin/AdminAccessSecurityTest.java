package com.campus.bounty.admin;

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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class AdminAccessSecurityTest {
    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired UserRepository userRepository;
    @Autowired UserService userService;
    @Autowired JwtUtil jwtUtil;
    @Autowired AdminService adminService;

    @Test
    void student_forbidden_admin_api() {
        User student = register("sec_student_1");
        ResponseEntity<Map> response = rest.exchange(url("/api/admin/reports"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(student)), Map.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void admin_allowed() {
        ResponseEntity<Map> login = rest.postForEntity(url("/api/auth/login"),
                Map.of("username", "admin", "password", "admin123"), Map.class);
        String token = ((Map<String, Object>) login.getBody().get("data")).get("token").toString();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<Map> response = rest.exchange(url("/api/admin/reports"), HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void student_report_path_and_dedupe() {
        User student = register("sec_student_2");
        Map<String, Object> body = Map.of("targetType", "POST", "targetId", 1, "reason", "测试举报");

        ResponseEntity<Map> first = rest.postForEntity(url("/api/reports"),
                new HttpEntity<>(body, authHeaders(student)), Map.class);
        assertEquals(HttpStatus.OK, first.getStatusCode());
        assertEquals(0, ((Number) first.getBody().get("code")).intValue());

        ResponseEntity<Map> duplicate = rest.postForEntity(url("/api/reports"),
                new HttpEntity<>(body, authHeaders(student)), Map.class);
        assertEquals(HttpStatus.OK, duplicate.getStatusCode());
        assertEquals(40000, ((Number) duplicate.getBody().get("code")).intValue());
    }

    @Test
    void banned_token_is_rejected() {
        User student = register("sec_student_3");
        HttpHeaders headers = authHeaders(student);
        Long adminId = userRepository.findByUsername("admin").orElseThrow().getId();

        adminService.banUser(adminId, student.getId());

        ResponseEntity<Map> response = rest.exchange(url("/api/trade/posts"), HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    private User register(String username) {
        return userService.register(username, "123456", "123456", "安全测试");
    }

    private HttpHeaders authHeaders(User user) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtUtil.generate(user.getId(), user.getUsername()));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
