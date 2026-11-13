package com.campus.bounty.config;

import com.campus.bounty.user.User;
import com.campus.bounty.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class TokenHandshakeInterceptor implements HandshakeInterceptor {
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler handler, Map<String, Object> attributes) {
        String token = UriComponentsBuilder.fromUri(request.getURI()).build()
                .getQueryParams().getFirst("token");
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            Long userId = jwtUtil.parse(token);
            User user = userRepository.findById(userId).orElse(null);
            if (user == null || "BANNED".equals(user.getStatus())) {
                return false;
            }
            attributes.put("userId", userId);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler handler, Exception exception) {
    }
}
