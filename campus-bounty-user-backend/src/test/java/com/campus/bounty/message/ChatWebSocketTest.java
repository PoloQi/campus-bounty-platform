package com.campus.bounty.message;

import com.campus.bounty.config.JwtUtil;
import com.campus.bounty.admin.AdminService;
import com.campus.bounty.user.User;
import com.campus.bounty.user.UserRepository;
import com.campus.bounty.user.UserService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    AdminService adminService;
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

            JsonNode acknowledged = read(senderListener);
            JsonNode pushed = read(receiverListener);
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
    void offline_first_message_has_a_conversation_entry_after_the_recipient_signs_in() throws Exception {
        User sender = register("chat_ws_offline_sender");
        User receiver = register("chat_ws_offline_receiver");
        MessageListener senderListener = new MessageListener();
        WebSocket senderSocket = connect(token(sender), senderListener);

        try {
            senderSocket.sendText("{\"toId\":" + receiver.getId()
                    + ",\"content\":\"离线消息\",\"requestId\":\"send-offline\"}", true).join();

            JsonNode acknowledged = read(senderListener);
            assertEquals("ACK", acknowledged.get("action").asText());
            assertEquals("send-offline", acknowledged.get("requestId").asText());
            assertEquals(receiver.getId(), acknowledged.get("toId").asLong());

            var url = httpUrl("/api/chat/conversations");
            assertEquals(HttpStatus.UNAUTHORIZED, rest.getForEntity(url, Map.class).getStatusCode());
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token(receiver));
            ResponseEntity<String> response = rest.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            JsonNode conversations = objectMapper.readTree(response.getBody()).get("data");
            assertEquals(1, conversations.size());
            assertEquals(sender.getId(), conversations.get(0).get("peerId").asLong());
            assertEquals(acknowledged.get("id"), conversations.get(0).get("lastMessageId"));
        } finally {
            senderSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
        }
    }

    @Test
    void delivers_to_all_tabs_and_keeps_the_older_tab_online_when_a_newer_tab_closes() throws Exception {
        User sender = register("chat_multi_sender");
        User receiver = register("chat_multi_receiver");
        MessageListener originalListener = new MessageListener();
        MessageListener newerListener = new MessageListener();
        MessageListener senderListener = new MessageListener();
        MessageListener senderReplicaListener = new MessageListener();
        WebSocket original = connect(token(receiver), originalListener);
        WebSocket newer = connect(token(receiver), newerListener);
        WebSocket senderReplica = connect(token(sender), senderReplicaListener);
        WebSocket sending = connect(token(sender), senderListener);

        try {
            sending.sendText("{\"toId\":" + receiver.getId() + ",\"content\":\"多个窗口\"}", true).join();
            JsonNode acknowledged = read(senderListener);
            JsonNode delivered = read(originalListener);
            assertEquals(acknowledged.get("id"), delivered.get("id"));
            assertEquals(delivered, read(newerListener));
            assertEquals(delivered, read(senderReplicaListener));

            newer.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
            newerListener.closeStatus.get(5, TimeUnit.SECONDS);
            sending.sendText("{\"toId\":" + receiver.getId() + ",\"content\":\"原窗口继续接收\"}", true).join();
            assertEquals("原窗口继续接收", read(originalListener).get("content").asText());
        } finally {
            sending.abort();
            senderReplica.abort();
            newer.abort();
            original.abort();
        }
    }

    @Test
    void banning_a_student_closes_all_old_chat_connections_before_the_next_push() throws Exception {
        User sender = register("chat_push_sender");
        User receiver = register("chat_push_receiver");
        User admin = register("chat_push_admin");
        admin.setRole("ADMIN");
        userRepository.saveAndFlush(admin);
        MessageListener firstListener = new MessageListener();
        MessageListener secondListener = new MessageListener();
        MessageListener senderListener = new MessageListener();
        WebSocket first = connect(token(receiver), firstListener);
        WebSocket second = connect(token(receiver), secondListener);
        WebSocket sending = connect(token(sender), senderListener);

        try {
            adminService.banUser(admin.getId(), receiver.getId());
            assertEquals(1008, firstListener.closeStatus.get(5, TimeUnit.SECONDS));
            assertEquals(1008, secondListener.closeStatus.get(5, TimeUnit.SECONDS));
            sending.sendText("{\"toId\":" + receiver.getId() + ",\"content\":\"封禁后的离线消息\"}", true).join();
            assertEquals("ACK", read(senderListener).get("action").asText());
            assertTrue(firstListener.messages.isEmpty());
            assertTrue(secondListener.messages.isEmpty());
        } finally {
            sending.abort();
            second.abort();
            first.abort();
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

            JsonNode error = read(listener);
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

            JsonNode error = read(senderListener);
            assertEquals("ERROR", error.get("action").asText());
            assertEquals("invalid-target", error.get("requestId").asText());
            assertEquals("接收用户不存在", error.get("error").asText());
            assertFalse(senderSocket.isOutputClosed());
        } finally {
            senderSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
        }
    }

    private JsonNode read(MessageListener listener) throws Exception {
        String frame = listener.messages.poll(5, TimeUnit.SECONDS);
        assertNotNull(frame, "没有收到预期的私信帧");
        return objectMapper.readTree(frame);
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
        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        private final CompletableFuture<Integer> closeStatus = new CompletableFuture<>();
        private final StringBuilder text = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            text.append(data);
            if (last) {
                messages.add(text.toString());
                text.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closeStatus.complete(statusCode);
            return null;
        }
    }
}
