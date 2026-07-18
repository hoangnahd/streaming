package com.app.streaming.config;

import com.app.streaming.handler.AdaptiveVideoBroadcastHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@EnableWebSocket
@Configuration
public class WebSocketConfig implements WebSocketConfigurer {
    private final AdaptiveVideoBroadcastHandler handler;

    public WebSocketConfig(AdaptiveVideoBroadcastHandler handler) {
        this.handler = handler;
    }
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/stream/{roomId}")
                .addInterceptors(new AuthHandshakeInterceptor())
                .setAllowedOrigins("https://localhost:8443");
    }
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean containerFactoryBean = new ServletServerContainerFactoryBean();

        containerFactoryBean.setMaxBinaryMessageBufferSize(10485760);
        containerFactoryBean.setMaxTextMessageBufferSize(10485760);

        return containerFactoryBean;
    }
}
