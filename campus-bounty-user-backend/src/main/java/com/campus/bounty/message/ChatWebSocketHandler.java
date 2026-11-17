package com.campus.bounty.message;

import com.campus.bounty.common.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
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
        Map<String, Object> reply = new HashMap<>();
        if (body.hasNonNull("requestId")) {
            reply.put("requestId", body.get("requestId").asText());
        }
        Message saved;
        try {
            saved = chatService.save(fromId, toId, content);
        } catch (BizException e) {
            reply.put("action", "ERROR");
            reply.put("error", e.getMessage());
            send(session, reply);
            return;
        }

        Map<String, Object> delivery = Map.of(
                "id", saved.getId(),
                "fromId", saved.getFromId(),
                "toId", saved.getToId(),
                "content", saved.getContent(),
                "createdAt", saved.getCreatedAt().toString());
        reply.putAll(delivery);
        reply.put("action", "ACK");
        send(session, reply);

        WebSocketSession target = online.get(toId);
        if (target != null && target != session) {
            send(target, delivery);
        }
    }

    private void send(WebSocketSession session, Map<String, Object> frame) {
        synchronized (session) {
            if (!session.isOpen()) {
                return;
            }
            try {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(frame)));
            } catch (IOException | IllegalStateException e) {
                log.debug("Could not send chat frame to session {}", session.getId(), e);
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
