package com.campus.bounty.message;

import com.campus.bounty.config.JwtUtil;
import com.campus.bounty.user.User;
import com.campus.bounty.user.UserRepository;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    UserRepository userRepository;
    @Autowired
    ChatService chatService;
    @Autowired
    JwtUtil jwtUtil;
    ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void websocket_acknowledges_saved_message_delivers_it_and_shows_it_in_history() throws Exception {
        User sender = register("chat_ws_sender");
        User receiver = register("chat_ws_receiver");
        String senderToken = token(sender);
        MessageListener receiverListener = new MessageListener();
        WebSocket receiverSocket = connect(token(receiver), receiverListener);
        MessageListener senderListener = new MessageListener();
        WebSocket senderSocket = connect(senderToken, senderListener);

        try {
            senderSocket.sendText("{\"toId\":" + receiver.getId()
                    + ",\"content\":\"实时你好\",\"requestId\":\"send-1\"}", true).join();

            JsonNode acknowledged = objectMapper.readTree(senderListener.nextText.get(5, TimeUnit.SECONDS));
            JsonNode pushed = objectMapper.readTree(receiverListener.nextText.get(5, TimeUnit.SECONDS));
            assertEquals("ACK", acknowledged.get("action").asText());
            assertEquals("send-1", acknowledged.get("requestId").asText());
            assertEquals(pushed.get("id"), acknowledged.get("id"));
            assertEquals(pushed.get("content"), acknowledged.get("content"));
            assertEquals(pushed.get("createdAt"), acknowledged.get("createdAt"));
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
            assertEquals(acknowledged.get("id").asLong(), ((Number) history.get(0).get("id")).longValue());
        } finally {
            senderSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
            receiverSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
        }
    }

    @Test
    void websocket_acknowledges_message_when_recipient_is_offline() throws Exception {
        User sender = register("chat_ws_offline_sender");
        User receiver = register("chat_ws_offline_receiver");
        MessageListener senderListener = new MessageListener();
        WebSocket senderSocket = connect(token(sender), senderListener);

        try {
            senderSocket.sendText("{\"toId\":" + receiver.getId()
                    + ",\"content\":\"离线消息\",\"requestId\":\"send-offline\"}", true).join();

            JsonNode acknowledged = objectMapper.readTree(senderListener.nextText.get(5, TimeUnit.SECONDS));
            assertEquals("ACK", acknowledged.get("action").asText());
            assertEquals("send-offline", acknowledged.get("requestId").asText());
            assertEquals(receiver.getId(), acknowledged.get("toId").asLong());
        } finally {
            senderSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
        }
    }

    @Test
    void websocket_rejects_invalid_token() {
        assertThrows(CompletionException.class, () -> connect("invalid-token", new MessageListener()));
    }

    @Test
    void banned_sender_cannot_store_messages_through_an_open_connection() throws Exception {
        User sender = register("chat_ws_banned_sender");
        User receiver = register("chat_ws_banned_receiver");
        MessageListener listener = new MessageListener();
        WebSocket socket = connect(token(sender), listener);
        try {
            sender.setStatus("BANNED");
            userRepository.saveAndFlush(sender);
            socket.sendText("{\"toId\":" + receiver.getId() + ",\"content\":\"你好\",\"requestId\":\"banned\"}",
                    true).join();

            JsonNode error = objectMapper.readTree(listener.nextText.get(5, TimeUnit.SECONDS));
            assertEquals("ERROR", error.get("action").asText());
            assertEquals("banned", error.get("requestId").asText());
            assertEquals("账号不可用", error.get("error").asText());
            assertTrue(chatService.history(sender.getId(), receiver.getId()).isEmpty());
        } finally {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
        }
    }

    @Test
    void websocket_returns_business_error_without_closing_sender() throws Exception {
        User sender = register("chat_ws_error_sender");
        MessageListener senderListener = new MessageListener();
        WebSocket senderSocket = connect(token(sender), senderListener);

        try {
            senderSocket.sendText("{\"toId\":99999999,\"content\":\"你好\",\"requestId\":\"invalid-target\"}",
                    true).join();

            JsonNode error = objectMapper.readTree(senderListener.nextText.get(5, TimeUnit.SECONDS));
            assertEquals("ERROR", error.get("action").asText());
            assertEquals("invalid-target", error.get("requestId").asText());
            assertEquals("接收用户不存在", error.get("error").asText());
            assertFalse(senderSocket.isOutputClosed());
        } finally {
            senderSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
        }
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
