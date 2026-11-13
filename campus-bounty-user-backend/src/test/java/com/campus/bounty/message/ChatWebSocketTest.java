package com.campus.bounty.message;

import com.campus.bounty.config.JwtUtil;
import com.campus.bounty.user.User;
import com.campus.bounty.user.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class ChatWebSocketTest {
    @LocalServerPort
    int port;
    @Autowired
    TestRestTemplate rest;
    @Autowired
    UserService userService;
    @Autowired
    JwtUtil jwtUtil;
    ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void websocket_delivers_message_and_rest_history_returns_it() throws Exception {
        User sender = register("chat_ws_sender");
        User receiver = register("chat_ws_receiver");
        String senderToken = token(sender);
        MessageListener receiverListener = new MessageListener();
        WebSocket receiverSocket = connect(token(receiver), receiverListener);
        WebSocket senderSocket = connect(senderToken, new MessageListener());

        try {
            senderSocket.sendText("{\"toId\":" + receiver.getId() + ",\"content\":\"实时你好\"}", true).join();

            JsonNode pushed = objectMapper.readTree(receiverListener.nextText.get(5, TimeUnit.SECONDS));
            assertEquals(sender.getId(), pushed.get("fromId").asLong());
            assertEquals(receiver.getId(), pushed.get("toId").asLong());
            assertEquals("实时你好", pushed.get("content").asText());
            assertFalse(pushed.get("id").isNull());

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(senderToken);
            ResponseEntity<Map> response = rest.exchange(httpUrl("/api/chat/history?withUserId=" + receiver.getId()),
                    HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            assertEquals(0, ((Number) response.getBody().get("code")).intValue());
            List<Map<String, Object>> history = (List<Map<String, Object>>) response.getBody().get("data");
            assertEquals(1, history.size());
            assertEquals("实时你好", history.get(0).get("content"));
        } finally {
            senderSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
            receiverSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
        }
    }

    @Test
    void websocket_rejects_invalid_token() {
        assertThrows(CompletionException.class, () -> connect("invalid-token", new MessageListener()));
    }

    private WebSocket connect(String token, WebSocket.Listener listener) {
        return HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create(wsUrl("/ws/chat?token=" + token)), listener).join();
    }

    private User register(String username) {
        return userService.register(username, "123456", "123456", "私信用户");
    }

    private String token(User user) {
        return jwtUtil.generate(user.getId(), user.getUsername());
    }

    private String wsUrl(String path) {
        return "ws://localhost:" + port + path;
    }

    private String httpUrl(String path) {
        return "http://localhost:" + port + path;
    }

    private static class MessageListener implements WebSocket.Listener {
        private final CompletableFuture<String> nextText = new CompletableFuture<>();

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            if (last) {
                nextText.complete(data.toString());
            }
            webSocket.request(1);
            return null;
        }
    }
}
