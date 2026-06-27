package com.brilliantseas.websocket;

import com.brilliantseas.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentWebSocketHandler extends TextWebSocketHandler {

    private static final Set<String> STAFF_ROLES = Set.of("STAFF", "ADMIN", "SUPERADMIN");

    private final JwtTokenProvider jwtTokenProvider;
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = extractToken(session.getUri());
        if (token == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Missing token"));
            return;
        }

        try {
            Claims claims = jwtTokenProvider.validateAccessToken(token);
            String role = jwtTokenProvider.getRoleFromClaims(claims);
            if (!STAFF_ROLES.contains(role)) {
                session.close(CloseStatus.POLICY_VIOLATION.withReason("Insufficient permissions"));
                return;
            }

            session.getAttributes().put("userId", claims.getSubject());
            session.getAttributes().put("role", role);
            sessions.add(session);
            session.sendMessage(new TextMessage("{\"type\":\"CONNECTED\"}"));
        } catch (JwtException e) {
            log.warn("Rejected appointment WebSocket connection: {}", e.getMessage());
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Invalid token"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    public void broadcast(String payload) {
        sessions.removeIf(session -> !session.isOpen());
        for (WebSocketSession session : sessions) {
            try {
                session.sendMessage(new TextMessage(payload));
            } catch (Exception e) {
                log.warn("Could not send appointment WebSocket event to {}", session.getId(), e);
                sessions.remove(session);
            }
        }
    }

    private String extractToken(URI uri) {
        if (uri == null) {
            return null;
        }
        return UriComponentsBuilder.fromUri(uri)
                .build()
                .getQueryParams()
                .getFirst("token");
    }
}
