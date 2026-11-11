package com.campus.bounty.user;

import com.campus.bounty.common.ApiResponse;
import com.campus.bounty.config.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@Import(LoginTest.TestProtectedController.class)
class LoginTest {
    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired UserRepository userRepository;

    @Test
    void register_then_login_returns_token_and_student_role() {
        ResponseEntity<Map> registered = register("stu_login_1");
        assertEquals(0, code(registered));

        ResponseEntity<Map> response = login("stu_login_1", "123456");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, code(response));
        Map<String, Object> data = data(response);
        assertNotNull(data.get("token"));
        assertFalse(data.get("token").toString().isBlank());
        assertEquals("STUDENT", data.get("role"));
        assertEquals("A", data.get("nickname"));
        assertNotNull(data.get("userId"));
        assertFalse(data.containsKey("passwordHash"));
    }

    @Test
    void valid_token_accesses_protected_endpoint_and_invalid_token_is_rejected() {
        register("stu_token");
        Map<String, Object> loginData = data(login("stu_token", "123456"));
        String token = loginData.get("token").toString();

        ResponseEntity<Map> valid = getProtected("Bearer " + token);
        assertEquals(HttpStatus.OK, valid.getStatusCode());
        assertEquals(0, code(valid));
        assertEquals(((Number) loginData.get("userId")).longValue(),
                ((Number) valid.getBody().get("data")).longValue());

        ResponseEntity<Map> invalid = getProtected("Bearer invalid-token");
        assertEquals(HttpStatus.UNAUTHORIZED, invalid.getStatusCode());
        assertEquals(40100, code(invalid));
    }

    @Test
    void wrong_password_is_rejected() {
        register("stu_wrong_password");

        ResponseEntity<Map> response = login("stu_wrong_password", "654321");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(40000, code(response));
        assertEquals("用户名或密码错误", response.getBody().get("message"));
    }

    @Test
    void banned_user_cannot_login() {
        register("stu_banned");
        User user = userRepository.findByUsername("stu_banned").orElseThrow();
        user.setStatus("BANNED");
        userRepository.saveAndFlush(user);

        ResponseEntity<Map> response = login("stu_banned", "123456");

        assertEquals(40000, code(response));
        assertEquals("账号已被封禁", response.getBody().get("message"));
    }

    @Test
    void invalid_registration_uses_unified_validation_response() {
        ResponseEntity<Map> response = rest.postForEntity(url("/api/auth/register"),
                Map.of("username", "ab", "password", "123456", "confirmPassword", "123456"), Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(40000, code(response));
        assertNotNull(response.getBody().get("message"));
    }

    private ResponseEntity<Map> register(String username) {
        return rest.postForEntity(url("/api/auth/register"), Map.of(
                "username", username,
                "password", "123456",
                "confirmPassword", "123456",
                "nickname", "A"), Map.class);
    }

    private ResponseEntity<Map> login(String username, String password) {
        return rest.postForEntity(url("/api/auth/login"),
                Map.of("username", username, "password", password), Map.class);
    }

    private ResponseEntity<Map> getProtected(String authorization) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, authorization);
        return rest.exchange(url("/api/test/protected"), HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
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

    @RestController
    static class TestProtectedController {
        @GetMapping("/api/test/protected")
        ApiResponse<Long> protectedResource() {
            return ApiResponse.ok(CurrentUser.id());
        }
    }
}
