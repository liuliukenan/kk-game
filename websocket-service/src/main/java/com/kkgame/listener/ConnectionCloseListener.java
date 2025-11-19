package com.kkgame.listener;

import com.google.protobuf.util.JsonFormat;
import com.kkgame.manager.SessionManager;
import com.kkgame.protobuf.ConnectionNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Component
public class ConnectionCloseListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(ConnectionCloseListener.class);

    public static final String CONNECTION_CLOSE_CHANNEL = "connection:close";

    @Autowired
    private SessionManager sessionManager;

    // 注入当前服务器ID
    @Value("${server.port}")
    private int serverPort;

    // 当前服务器标识
    private String currentServerId;

    @javax.annotation.PostConstruct
    public void init() throws java.net.UnknownHostException {
        // 获取本机IP地址
        java.net.InetAddress localHost = java.net.InetAddress.getLocalHost();
        // 服务器IP地址
        String serverIpAddress = localHost.getHostAddress();
        currentServerId = serverIpAddress + ":" + serverPort;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel());
        String messageBody = new String(message.getBody());
        log.info("收到消息: 频道={}, 内容={}", channel, messageBody);
        try {
            if (CONNECTION_CLOSE_CHANNEL.equals(channel)) {
                // 处理连接关闭消息
                handleCloseConnection(messageBody);
            }
        } catch (Exception e) {
            log.error("处理消息时出错: {}", messageBody, e);
        }
    }

    /**
     * 处理连接关闭消息
     * @param messageBody 消息内容 (格式: userId:sessionId:serverId)
     */
    private void handleCloseConnection(String messageBody) {
        try {
            // 解析连接通知消息
            ConnectionNotification.Builder builder = ConnectionNotification.newBuilder();
            JsonFormat.parser().merge(messageBody, builder);
            ConnectionNotification notification = builder.build();

            String userId = notification.getUserId();
            String sessionId = notification.getSessionId();
            String serverId = notification.getServerId();

            // 判断是否是当前服务器发送的消息，如果是则跳过处理
            if (currentServerId.equals(serverId)) {
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
