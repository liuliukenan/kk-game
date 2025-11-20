package com.kkgame.manager;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class SessionManager {

    // 使用userId作为key的会话映射
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /**
     * 添加会话
     * @param userId 用户ID
     * @param session WebSocket会话
     */
    public void addSession(String userId, WebSocketSession session) {
        sessions.put(userId, session);
        log.info("添加会话: 用户ID={}, 会话ID={}", userId, session.getId());
    }

    /**
     * 移除会话
     * @param userId 用户ID
     */
    public void removeSession(String userId) {
        WebSocketSession removed = sessions.remove(userId);
        if (removed != null) {
            log.info("移除会话: 用户ID={}, 会话ID={}", userId, removed.getId());
        }
    }

    /**
     * 获取会话
     * @param userId 用户ID
     * @return WebSocket会话，如果不存在则返回null
     */
    public WebSocketSession getSession(String userId) {
        return sessions.get(userId);
    }

    /**
     * 检查会话是否存在且处于打开状态
     * @param userId 用户ID
     * @return 如果会话存在且打开则返回true，否则返回false
     */
    public boolean isSessionOpen(String userId) {
        WebSocketSession session = sessions.get(userId);
        return session != null && session.isOpen();
    }

    /**
     * 关闭指定会话
     * @param userId 用户ID
     * @param reason 关闭原因
     */
    public void closeSession(String userId, String reason) {
        WebSocketSession session = sessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.close(CloseStatus.GOING_AWAY.withReason(reason));
                log.info("连接 {} 已关闭，原因: {}", session.getId(), reason);
            } catch (Exception e) {
                log.error("关闭连接 {} 时出错", session.getId(), e);
            }
        } else {
            log.info("连接对应的用户 {} 不存在或已关闭", userId);
        }
    }

    /**
     * 获取当前会话总数
     * @return 会话总数
     */
    public int getTotalSessionCount() {
        return sessions.size();
    }
}
