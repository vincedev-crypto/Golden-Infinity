package com.brilliantseas.config;

import com.brilliantseas.websocket.AppointmentWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final AppointmentWebSocketHandler appointmentWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(appointmentWebSocketHandler, "/api/v1/ws/appointments")
                .setAllowedOrigins("http://localhost:3000", "http://localhost:5500", "http://localhost:8080");
    }
}
