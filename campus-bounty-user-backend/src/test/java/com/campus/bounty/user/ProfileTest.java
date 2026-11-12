package com.campus.bounty.user;

import com.campus.bounty.config.JwtUtil;
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

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class ProfileTest {
    @LocalServerPort
    int port;
    @Autowired
    TestRestTemplate rest;
    @Autowired
    UserService userService;
    @Autowired
    JwtUtil jwtUtil;

    @Test
    void current_user_can_read_profile_without_password_hash() {
        User user = register("profile_read_user");

        ResponseEntity<Map> response = rest.exchange(url("/api/users/me"),
                HttpMethod.GET, new HttpEntity<>(authHeaders(user)), Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, code(response));
        Map<String, Object> data = data(response);
        assertEquals(user.getId(), ((Number) data.get("id")).longValue());
        assertEquals("profile_read_user", data.get("username"));
        assertFalse(data.containsKey("passwordHash"));
        assertNull(data.get("bio"));
    }

    @Test
    void current_user_can_update_editable_profile_fields_only() {
        User user = register("profile_update_user");

        Map<String, Object> update = new HashMap<>();
        update.put("nickname", "新昵称");
        update.put("avatar", "/uploads/avatar.png");
        update.put("bio", "热爱学习");
        update.put("contact", "wx_123456");
        ResponseEntity<Map> updated = rest.exchange(url("/api/users/me"),
                HttpMethod.PUT, new HttpEntity<>(update, authHeaders(user)), Map.class);
        assertEquals(HttpStatus.OK, updated.getStatusCode());
        assertEquals(0, code(updated));

        Map<String, Object> data = data(updated);
        assertEquals("新昵称", data.get("nickname"));
        assertEquals("/uploads/avatar.png", data.get("avatar"));
        assertEquals("热爱学习", data.get("bio"));
        assertEquals("wx_123456", data.get("contact"));
        assertEquals("profile_update_user", data.get("username"));
        assertEquals("STUDENT", data.get("role"));

        Map<String, Object> clear = new HashMap<>();
        clear.put("bio", "");
        ResponseEntity<Map> cleared = rest.exchange(url("/api/users/me"),
                HttpMethod.PUT, new HttpEntity<>(clear, authHeaders(user)), Map.class);
        assertEquals(0, code(cleared));
        assertNull(data(cleared).get("bio"));
        assertEquals("新昵称", data(cleared).get("nickname"));
    }

    @Test
    void profile_update_rejects_too_long_field() {
        User user = register("profile_length_user");

        Map<String, Object> update = new HashMap<>();
        update.put("nickname", "a".repeat(31));
        ResponseEntity<Map> response = rest.exchange(url("/api/users/me"),
                HttpMethod.PUT, new HttpEntity<>(update, authHeaders(user)), Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(40000, code(response));
    }

    private User register(String username) {
        return userService.register(username, "123456", "123456", "资料测试");
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ResponseEntity<Map> response) {
        return (Map<String, Object>) response.getBody().get("data");
    }
}
