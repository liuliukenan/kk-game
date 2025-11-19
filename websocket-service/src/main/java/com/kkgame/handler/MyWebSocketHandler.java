package com.kkgame.handler;

import com.google.protobuf.util.JsonFormat;
import com.kkgame.api.DubboApi;
import com.kkgame.constans.RedisKeyUtil;
import com.kkgame.enums.ServerNameEnum;
import com.kkgame.listener.ConnectionCloseListener;
import com.kkgame.manager.SessionManager;
import com.kkgame.protobuf.ConnectionNotification;
import com.kkgame.protobuf.MessageData;
import com.kkgame.util.ClientApiManager;
import com.kkgame.util.DubboServiceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.PostConstruct;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

@Component
public class MyWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(MyWebSocketHandler.class);

    // 注入Redis模板
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // 注入会话管理器
    @Autowired
    private SessionManager sessionManager;

    // 注入服务器端口
    @Value("${server.port}")
    private int serverPort;

    // 当前服务器标识
    private String currentServerId;

    @PostConstruct
    public void init() throws UnknownHostException {
        // 获取本机IP地址
        InetAddress localHost = InetAddress.getLocalHost();
        // 服务器IP地址
        String serverIpAddress = localHost.getHostAddress();
        currentServerId = serverIpAddress + ":" + serverPort;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = (String) session.getAttributes().get("userId");

        if (userId != null) {
            // 检查本地是否已存在该用户的连接
            if (sessionManager.isSessionOpen(userId)) {
                sessionManager.closeSession(userId, "New connection established on same server");
                log.info("关闭用户 {} 在当前服务器上的旧连接", userId);
            }

            // 向所有服务实例广播连接建立消息
            notifyAllInstances(userId, session.getId());

            // 建立新连接
            sessionManager.addSession(userId, session);

            log.info("建立认证链接: {}，用户ID: {}，服务器: {}", session.getId(), userId, currentServerId);

            // 存储用户与websocket实例的映射关系到Redis
            storeUserWebSocketMapping(userId);
        } else {
            // 这种情况不应该发生，因为拦截器已经拒绝了匿名连接
            log.error("意外的匿名连接: {}", session.getId());
        }
    }

    /**
     * 通知所有服务实例有新连接建立
     * @param userId 用户ID
     * @param sessionId 新会话ID
     */
    private void notifyAllInstances(String userId, String sessionId) {
        // 构造连接通知消息对象
        ConnectionNotification notification = ConnectionNotification.newBuilder()
                .setUserId(userId)
                .setSessionId(sessionId)
                .setServerId(currentServerId)
                .build();

        // 序列化为JSON字符串发送
        try {
            String message = com.google.protobuf.util.JsonFormat.printer().print(notification);
            stringRedisTemplate.convertAndSend(ConnectionCloseListener.CONNECTION_CLOSE_CHANNEL, message);
            log.info("广播新连接消息: {}", message);
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            log.error("序列化连接通知消息失败", e);
        }
    }

    /**
     * 存储用户与websocket实例的映射关系到Redis
     * @param userId 用户ID
     */
    private void storeUserWebSocketMapping(String userId) {
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

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        try {
            // 处理二进制消息（Protocol Buffers 格式）
            ByteBuffer payload = message.getPayload();
            byte[] bytes = new byte[payload.remaining()];
            payload.get(bytes);

            // 解析 Protocol Buffers 消息
            MessageData messageData = MessageData.parseFrom(bytes);

            log.info("收到客户端发送的消息 {}", JsonFormat.printer().print(messageData));

            String serverName = ServerNameEnum.getServerNameString(messageData.getServerName());
            if (ServerNameEnum.NONE.getServerName().equals(serverName)) {
                log.error("服务未找到 {}", messageData);
                return;
            }

            String sessionId = session.getId();

            // 使用公共模块的方法处理消息
            DubboApi api = ClientApiManager.fetchClientApi(sessionId, serverName);

            // 构建新消息
            MessageData newMessageData = messageData.toBuilder().setClientId(sessionId).build();
            // 将消息发送给已分配的服务处理
            log.info("转发给{}服务, clientId: {}, message: {}",
                    serverName, sessionId, com.google.protobuf.util.JsonFormat.printer().print(newMessageData));
            if (api != null) {
                api.processMessageProto(newMessageData.toByteArray());
            }
        } catch (Exception e) {
            log.error("Error handling binary message", e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = (String) session.getAttributes().get("userId");
        if (userId != null) {
            sessionManager.removeSession(userId);

            // 连接关闭时清理缓存
            ClientApiManager.remove(session.getId());
            // 清理Redis中的用户与websocket实例映射关系
            clearUserWebSocketMapping(userId);
            log.info("WebSocket connection closed: {}，用户ID: {}，服务器: {}", session.getId(), userId, currentServerId);
        } else {
            // 匿名连接不应该发生，但为了安全起见还是处理一下
            ClientApiManager.remove(session.getId());
            clearUserWebSocketMapping(session.getId());
            log.info("WebSocket connection closed: {}", session.getId());
        }
    }

    /**
     * 清理Redis中的用户与websocket实例映射关系
     * @param userId 用户ID
     */
    private void clearUserWebSocketMapping(String userId) {
        try {
            String redisKey = RedisKeyUtil.USER_SERVER_KEY + ServerNameEnum.WEBSOCKET_SERVICE.getServerName();
            stringRedisTemplate.opsForHash().delete(redisKey, userId);
            log.info("Cleared user to websocket instance mapping for user: {}", userId);
        } catch (Exception e) {
            log.error("Failed to clear user to websocket instance mapping for user: {}", userId, e);
        }
    }

    public void pushMessageToClientProto(MessageData messageData) throws Exception {
        WebSocketSession session = sessionManager.getSession(messageData.getClientId());
        if (session != null && session.isOpen()) {
            // 使用 BinaryMessage 传输 Protocol Buffers 的二进制数据
            ByteBuffer buffer = ByteBuffer.wrap(messageData.toByteArray());
            session.sendMessage(new BinaryMessage(buffer));
            log.info("pushMessageToClientProto,clientId:{} message: {}", messageData.getClientId(), messageData.getMessage());
        }
    }

    /**
     * 获取总的连接数
     * @return 总连接数
     */
    public int getTotalConnectionCount() {
        return sessionManager.getTotalSessionCount();
    }
}
