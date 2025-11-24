package com.kkgame.handler;

import com.kkgame.service.WebSocketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.Resource;

@Component
@Slf4j
public class MyWebSocketHandler extends TextWebSocketHandler {

    // 注入WebSocket服务
    @Resource
    private WebSocketService webSocketService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.debug("New WebSocket connection established - Session ID: {}", session.getId());
        webSocketService.handleConnectionEstablished(session);
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        log.debug("Received binary messageSession ID: {}, Close Status: {}",
                session.getId(), message.getPayloadLength());
        webSocketService.handleBinaryMessage(session, message);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        // 添加日志记录，使用 session 和 status 参数
        log.debug("WebSocket connection closed - Session ID: {}, Close Status: {}",
                session.getId(), status.getCode());
        webSocketService.handleConnectionClosed(session, status);
    }
}
