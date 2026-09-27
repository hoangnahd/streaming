package com.app.streaming.config;

import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

public class AuthHandshakeInterceptor implements HandshakeInterceptor {

    private static final Set<String> ALLOWED_ORIGINS;
    private static final Logger log = LoggerFactory.getLogger(AuthHandshakeInterceptor.class);

    static {
        // Read the comma-separated origins from the system environment
        String envOrigins = System.getenv("ALLOWED_ORIGINS");
        
        if (envOrigins != null && !envOrigins.isBlank()) {
            ALLOWED_ORIGINS = Set.of(envOrigins.split(","));
        } else {
            // Fallback for local development
            ALLOWED_ORIGINS = Set.of("http://localhost:5500");
        }
    }


    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {

        log.info("Entering handshake for URI: {}", request.getURI());
        String origin = request.getHeaders().getFirst("Origin");
        if (origin == null || !ALLOWED_ORIGINS.contains(origin)) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        attributes.put("username", auth.getName());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }
}
