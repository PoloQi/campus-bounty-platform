package com.campus.bounty.wallet;

import com.campus.bounty.common.BizException;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class WalletRechargeTest {
    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired UserService userService;
    @Autowired JwtUtil jwtUtil;
    @Autowired WalletService walletService;

    @Test
    void recharge_increases_balance_and_writes_recharge_log() {
        User user = register("wallet_recharge_service");

        Wallet wallet = walletService.recharge(user.getId(), new BigDecimal("100.00"));

        assertMoney("100.00", wallet.getBalance());
        assertMoney("0", wallet.getFrozen());
        List<WalletLog> logs = walletService.logs(user.getId());
        assertEquals(1, logs.size());
        assertEquals("RECHARGE", logs.get(0).getType());
        assertMoney("100.00", logs.get(0).getAmount());
        assertMoney("100.00", logs.get(0).getBalanceAfter());
        assertNull(logs.get(0).getOrderId());
    }

    @Test
    void recharge_rejects_null_non_positive_and_excess_precision_amounts() {
        User user = register("wallet_recharge_invalid");

        assertThrows(BizException.class, () -> walletService.recharge(user.getId(), null));
        assertThrows(BizException.class, () -> walletService.recharge(user.getId(), BigDecimal.ZERO));
        assertThrows(BizException.class, () -> walletService.recharge(user.getId(), new BigDecimal("-1")));
        assertThrows(BizException.class, () -> walletService.recharge(user.getId(), new BigDecimal("0.001")));
        assertMoney("0", walletService.getOrCreate(user.getId()).getBalance());
        assertEquals(0, walletService.logs(user.getId()).size());
    }

    @Test
    void authenticated_user_can_query_recharge_and_list_own_logs_over_http() {
        User user = register("wallet_recharge_http");
        HttpHeaders headers = authHeaders(user);

        ResponseEntity<Map> initial = exchange("/api/wallet", HttpMethod.GET,
                new HttpEntity<>(headers));
        assertEquals(HttpStatus.OK, initial.getStatusCode());
        assertEquals(0, code(initial));
        assertMoney("0", data(initial).get("balance"));

        ResponseEntity<Map> recharged = exchange("/api/wallet/recharge", HttpMethod.POST,
                new HttpEntity<>(Map.of("amount", "25.50"), headers));
        assertEquals(0, code(recharged));
        assertMoney("25.50", data(recharged).get("balance"));

        ResponseEntity<Map> logs = exchange("/api/wallet/logs", HttpMethod.GET,
                new HttpEntity<>(headers));
        assertEquals(0, code(logs));
        List<Map<String, Object>> items = (List<Map<String, Object>>) logs.getBody().get("data");
        assertEquals(1, items.size());
        assertEquals("RECHARGE", items.get(0).get("type"));
        assertMoney("25.50", items.get(0).get("amount"));
    }

    @Test
    void wallet_http_endpoints_reject_missing_token() {
        ResponseEntity<Map> response = rest.getForEntity(url("/api/wallet"), Map.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(40100, code(response));
    }

    private User register(String username) {
        return userService.register(username, "123456", "123456", "钱包测试");
    }

    private HttpHeaders authHeaders(User user) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtUtil.generate(user.getId(), user.getUsername()));
        return headers;
    }

    private ResponseEntity<Map> exchange(String path, HttpMethod method, HttpEntity<?> request) {
        return rest.exchange(url(path), method, request, Map.class);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private int code(ResponseEntity<Map> response) {
        return ((Number) response.getBody().get("code")).intValue();
    }

    private Map<String, Object> data(ResponseEntity<Map> response) {
        return (Map<String, Object>) response.getBody().get("data");
    }

    private void assertMoney(String expected, Object actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(actual.toString())));
    }
}
