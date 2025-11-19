package com.kkgame.interceptor;

import com.kkgame.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class JwtAuthenticationInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationInterceptor.class);

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                 WebSocketHandler wsHandler, Map<String, Object> attributes) {

        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest httpRequest = servletRequest.getServletRequest();
            String token = httpRequest.getHeader("token");
            String clientIP = getClientIP(httpRequest);

            if (token != null && !token.isEmpty()) {
                try {
                    // 验证JWT令牌并提取用户ID
                    String userId = jwtUtil.extractUserId(token);
                    if (jwtUtil.validateToken(token, userId)) {
                        // 将用户信息存储在attributes中，供后续使用
                        attributes.put("userId", userId);
                        attributes.put("clientIP", clientIP);
                        log.info("WebSocket认证成功，用户ID: {}，客户端IP: {}", userId, clientIP);
                        return true;
                    }
                } catch (Exception e) {
                    log.error("JWT令牌验证失败", e);
                }
            }
        }

        // 不允许匿名连接
        log.info("WebSocket连接认证失败，拒绝匿名连接");
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                             WebSocketHandler wsHandler, Exception exception) {
        // 握手后不需要特殊处理
    }

    /**
     * 获取客户端真实IP地址
     */
    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }
}
