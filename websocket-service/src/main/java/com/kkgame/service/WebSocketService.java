package com.kkgame.service;

import com.google.protobuf.util.JsonFormat;
import com.kkgame.api.DubboApi;
import com.kkgame.constans.RedisKeyUtil;
import com.kkgame.enums.ServerNameEnum;
import com.kkgame.manager.SessionManager;
import com.kkgame.protobuf.ConnectionNotification;
import com.kkgame.protobuf.MessageData;
import com.kkgame.protobuf.ServerName;
import com.kkgame.util.ClientApiManager;
import com.kkgame.util.DubboServiceUtil;
import com.kkgame.util.ServerIdUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.nio.ByteBuffer;

@Service
@Slf4j
public class WebSocketService {

    // 注入Redis模板
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // 注入会话管理器
    @Autowired
    private SessionManager sessionManager;

    public void handleConnectionEstablished(WebSocketSession session) {
        String userId = (String) session.getAttributes().get("userId");

        if (userId != null) {
            // 检查本地是否已存在该用户的连接
            if (isSessionOpen(userId)) {
                closeSession(userId, "New connection established on same server");
                log.info("关闭用户 {} 在当前服务器上的旧连接", userId);
            }

            // 向所有服务实例广播连接建立消息
            notifyAllInstances(userId, session.getId());

            // 建立新连接
            addSession(userId, session);

            // 存储用户与websocket实例的映射关系到Redis
            storeUserWebSocketMapping(userId);
        } else {
            // 这种情况不应该发生，因为拦截器已经拒绝了匿名连接
            log.error("意外的匿名连接: {}", session.getId());
        }
    }

    public void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        try {
            // 处理二进制消息（Protocol Buffers 格式）
            ByteBuffer payload = message.getPayload();
            byte[] bytes = new byte[payload.remaining()];
            payload.get(bytes);

            // 解析 Protocol Buffers 消息
            MessageData messageData = MessageData.parseFrom(bytes);

            log.info("收到客户端发送的消息 {}", JsonFormat.printer().print(messageData));

            String serverName = getServerNameString(messageData.getServerName());
            if (isNoneServer(serverName)) {
                log.error("服务未找到 {}", messageData);
                return;
            }

            String userId = (String) session.getAttributes().get("userId");
            // 构建新消息
            MessageData newMessageData = messageData.toBuilder().setUserId(userId).build();

            // 将消息发送给已分配的服务处理
            log.info("转发给{}服务, userId: {}, message: {}", serverName, userId, JsonFormat.printer().print(newMessageData));

            // 使用公共模块的方法处理消息
            DubboApi api = ClientApiManager.fetchClientApi(userId, serverName);
            if (api != null) {
                api.processMessageProto(newMessageData.toByteArray());
            }
        } catch (Exception e) {
            log.error("Error handling binary message", e);
        }
    }

    public void handleConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = (String) session.getAttributes().get("userId");
        String sessionId = session.getId();
        if (userId != null) {
            removeSession(userId);
            // 连接关闭时清理缓存
            removeClientApi(sessionId);
            // 清理Redis中的用户与websocket实例映射关系
            clearUserWebSocketMapping(userId);
            log.info("WebSocket connection closed: {}，用户ID: {}，服务器: {} status:{}", sessionId, userId, ServerIdUtil.fetchLocalServerId(), status);
        } else {
            // 匿名连接不应该发生，但为了安全起见还是处理一下
            removeClientApi(sessionId);
            clearUserWebSocketMapping(sessionId);
            log.info("WebSocket connection closed: {}", sessionId);
        }
    }

    /**
     * 通知所有服务实例有新连接建立
     * @param userId 用户ID
     * @param sessionId 新会话ID
     */
    public void notifyAllInstances(String userId, String sessionId) {
        // 构造连接通知消息对象
        ConnectionNotification notification = ConnectionNotification.newBuilder()
                .setUserId(userId)
                .setSessionId(sessionId)
                .setServerId(ServerIdUtil.fetchLocalServerId())
                .build();

        try {
            String message = JsonFormat.printer().print(notification);
            log.info("广播新连接消息: {}", message);
            // 使用Base64编码二进制数据以便通过StringRedisTemplate传输
            String encodedData = java.util.Base64.getUrlEncoder().encodeToString(notification.toByteArray());
            stringRedisTemplate.convertAndSend("connection:close", encodedData);
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            log.error("序列化连接通知消息失败", e);
        }
    }

    /**
     * 存储用户与websocket实例的映射关系到Redis
     * @param userId 用户ID
     */
    public void storeUserWebSocketMapping(String userId) {
        try {
            String serverName = ServerNameEnum.WEBSOCKET_SERVICE.getServerName();
            String redisKey = RedisKeyUtil.USER_SERVER_KEY + serverName;
            // 使用Dubbo服务地址，用于其他服务与WS服务通信
            String ipAndPort = DubboServiceUtil.getLocalServiceInstanceInfo();

            // 使用 serverName 作为 key，ipAndPort 作为 value 存储
            stringRedisTemplate.opsForHash().put(redisKey, userId, ipAndPort);
            log.info("玩家ws链接信息写入redis {}: {}", userId, ipAndPort);
        } catch (Exception e) {
            log.error("Failed to store user to websocket instance mapping for user: {}", userId, e);
        }
    }

    /**
     * 清理Redis中的用户与websocket实例映射关系
     * @param userId 用户ID
     */
    public void clearUserWebSocketMapping(String userId) {
        try {
            String redisKey = RedisKeyUtil.USER_SERVER_KEY + ServerNameEnum.WEBSOCKET_SERVICE.getServerName();
            stringRedisTemplate.opsForHash().delete(redisKey, userId);
            log.info("Cleared user to websocket instance mapping for user: {}", userId);
        } catch (Exception e) {
            log.error("Failed to clear user to websocket instance mapping for user: {}", userId, e);
        }
    }

    public void pushMessageToClientProto(MessageData messageData) throws Exception {
        // 注意：这里messageData.getUserId()现在返回的是userId而不是sessionId
        WebSocketSession session = sessionManager.getSession(messageData.getUserId());
        if (session != null && session.isOpen()) {
            // 使用 BinaryMessage 传输 Protocol Buffers 的二进制数据
            ByteBuffer buffer = ByteBuffer.wrap(messageData.toByteArray());
            session.sendMessage(new org.springframework.web.socket.BinaryMessage(buffer));
            log.info("pushMessageToClientProto,userId:{} message: {}", messageData.getUserId(), messageData.getMessage());
        }
    }

    /**
     * 获取总的连接数
     * @return 总连接数
     */
    public int getTotalConnectionCount() {
        return sessionManager.getTotalSessionCount();
    }

    public boolean isSessionOpen(String userId) {
        return sessionManager.isSessionOpen(userId);
    }

    public void closeSession(String userId, String reason) {
        sessionManager.closeSession(userId, reason);
    }

    public void addSession(String userId, WebSocketSession session) {
        sessionManager.addSession(userId, session);
    }

    public void removeSession(String userId) {
        sessionManager.removeSession(userId);
    }

    public String getServerNameString(ServerName serverName) {
        return ServerNameEnum.getServerNameString(serverName);
    }

    public boolean isNoneServer(String serverName) {
        return ServerNameEnum.NONE.getServerName().equals(serverName);
    }

    public void removeClientApi(String userId) {
        ClientApiManager.remove(userId);
    }

}
