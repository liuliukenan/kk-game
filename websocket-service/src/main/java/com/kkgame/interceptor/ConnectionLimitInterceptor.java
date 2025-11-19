package com.kkgame.interceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class ConnectionLimitInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ConnectionLimitInterceptor.class);

    // 限制每个用户每分钟最多建立连接次数
    private static final int MAX_CONNECTIONS_PER_MINUTE = 10;

    // 限制时间窗口（分钟）
    private static final int TIME_WINDOW_MINUTES = 1;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                 WebSocketHandler wsHandler, Map<String, Object> attributes) {

        if (request instanceof ServletServerHttpRequest) {
            String userId = (String) attributes.get("userId");

            if (userId != null) {
                // 检查分布式连接限制
                String redisKey = "connection_limit:" + userId;

                // 获取当前计数
                String countStr = stringRedisTemplate.opsForValue().get(redisKey);
                int currentCount = countStr != null ? Integer.parseInt(countStr) : 0;

                // 检查是否超过限制
                if (currentCount >= MAX_CONNECTIONS_PER_MINUTE) {
                    log.warn("用户 {} 连接频率超过限制，当前连接数: {}", userId, currentCount);
                    response.setStatusCode(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS);
                    return false;
                }

                // 增加计数器
                stringRedisTemplate.opsForValue().increment(redisKey, 1);

                // 设置过期时间
                stringRedisTemplate.expire(redisKey, TIME_WINDOW_MINUTES, TimeUnit.MINUTES);

                log.info("用户 {} 建立WebSocket连接，当前分钟内连接次数: {}", userId, currentCount + 1);
            }
        }

        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                             WebSocketHandler wsHandler, Exception exception) {
        // 握手后不需要特殊处理
    }
}
