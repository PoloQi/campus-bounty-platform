package com.campus.bounty.message;

import com.campus.bounty.common.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private final ChatService chatService;
    private final ObjectMapper objectMapper;
    private final Map<Long, WebSocketSession> online = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        online.put(userId(session), session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) throws IOException {
        JsonNode body = objectMapper.readTree(textMessage.getPayload());
        if (!body.hasNonNull("toId") || !body.hasNonNull("content")) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        Long fromId = userId(session);
        Long toId = body.get("toId").asLong();
        String content = body.get("content").asText();
        Message saved;
        try {
            saved = chatService.save(fromId, toId, content);
        } catch (BizException e) {
            String payload = objectMapper.writeValueAsString(Map.of("error", e.getMessage()));
            synchronized (session) {
                session.sendMessage(new TextMessage(payload));
            }
            return;
        }

        WebSocketSession target = online.get(toId);
        if (target != null && target.isOpen()) {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "id", saved.getId(),
                    "fromId", fromId,
                    "toId", toId,
                    "content", content));
            synchronized (target) {
                target.sendMessage(new TextMessage(payload));
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        online.remove(userId(session), session);
    }

    private Long userId(WebSocketSession session) {
        return (Long) session.getAttributes().get("userId");
    }
}
