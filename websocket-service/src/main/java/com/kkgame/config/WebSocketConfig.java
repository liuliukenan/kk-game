package com.kkgame.config;

import com.kkgame.handler.MyWebSocketHandler;
import com.kkgame.interceptor.JwtAuthenticationInterceptor;
import com.kkgame.interceptor.ConnectionLimitInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket配置类，用于配置WebSocket端点和拦截器
 * 提供带认证和不带认证两种连接方式
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private MyWebSocketHandler myWebSocketHandler;
    
    @Autowired
    private JwtAuthenticationInterceptor jwtAuthenticationInterceptor;
    
    @Autowired
    private ConnectionLimitInterceptor connectionLimitInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(myWebSocketHandler, "/websocket")
                .addInterceptors(jwtAuthenticationInterceptor, connectionLimitInterceptor)
                .setAllowedOrigins("*");
    }
}