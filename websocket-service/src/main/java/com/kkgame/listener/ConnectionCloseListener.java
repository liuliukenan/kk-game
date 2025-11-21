package com.kkgame.listener;

import com.google.protobuf.util.JsonFormat;
import com.kkgame.manager.SessionManager;
import com.kkgame.protobuf.ConnectionNotification;
import com.kkgame.util.CommonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
@Slf4j
public class ConnectionCloseListener implements MessageListener {

    public static final String CONNECTION_CLOSE_CHANNEL = "connection:close";

    @Resource
    private SessionManager sessionManager;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel());
        byte[] body = message.getBody();
        log.info("收到redis监听消息: channel={}", channel);
        try {
            if (CONNECTION_CLOSE_CHANNEL.equals(channel)) {
                // 处理连接关闭消息
                handleCloseConnection(body);
            }
        } catch (Exception e) {
            log.error("处理消息时出错 ", e);
        }
    }

    /**
     * 处理连接关闭消息
     * @param messageBody 消息内容 (格式: userId:sessionId:serverId)
     */
    private void handleCloseConnection(byte[] messageBody) {
        try {
            // 解析连接通知消息
            String messageString = new String(messageBody);
            byte[] decodedData = java.util.Base64.getUrlDecoder().decode(messageString);
            ConnectionNotification notification = ConnectionNotification.parseFrom(decodedData);
            log.info("handleCloseConnection message: {}", JsonFormat.printer().print(notification));

            String userId = notification.getUserId();
            String sessionId = notification.getSessionId();
            String serverId = notification.getServerId();

            // 判断是否是当前服务器发送的消息，如果是则跳过处理
            if (CommonUtil.fetchLocalServerId().equals(serverId)) {
                log.info("收到本服务器发出的消息，跳过处理: 用户 {} 在服务器 {} 上的连接 {}", userId, serverId, sessionId);
                return;
            }

            // 直接检查并关闭本地会话
            if (sessionManager.isSessionOpen(userId)) {
                sessionManager.closeSession(userId, "Connection closed by remote server");
                log.info("用户 {} 在当前服务器上的连接已关闭", userId);
            } else {
                log.info("用户 {} 在服务器 {} 上的连接 {} 不在当前服务器上，无需处理", userId, serverId, sessionId);
            }
        } catch (Exception e) {
            log.error("解析连接通知消息失败: {}", messageBody, e);
        }
    }
}
