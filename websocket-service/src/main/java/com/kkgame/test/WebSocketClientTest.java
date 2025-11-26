package com.kkgame.test;

import com.kkgame.protobuf.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.websocket.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class WebSocketClientTest {
    private static final int CLIENT_COUNT = 10; // 并发客户端数量
    private static final int MESSAGE_PER_CLIENT = 10000; // 每个客户端发送消息数量

    private static final List<TestWebSocketClient> clients = new ArrayList<>();
    private static final AtomicInteger successConnections = new AtomicInteger(0);
    private static final AtomicInteger failedConnections = new AtomicInteger(0);
    private static final AtomicInteger receivedMessages = new AtomicInteger(0);
    private static final AtomicInteger sentMessages = new AtomicInteger(0);

    public static void main(String[] args) throws InterruptedException {
        String wsUrl = "ws://localhost:8084/websocket";
        log.info("开始WebSocket性能测试...");
        log.info("目标: " + CLIENT_COUNT + " 个并发客户端，每个客户端发送 " + MESSAGE_PER_CLIENT + " 条消息");




        // 创建并发连接
        for (int clientId = 0; clientId < CLIENT_COUNT; clientId++) {
            try {
                TestWebSocketClient client = new TestWebSocketClient(clientId, wsUrl);
                client.connect();
                clients.add(client);
                successConnections.incrementAndGet();
            } catch (Exception e) {
                System.err.println("客户端 " + clientId + " 连接失败: " + e);
                failedConnections.incrementAndGet();
            }
        }

        log.info("连接阶段完成:");
        log.info("- 成功连接: " + successConnections.get());
        log.info("- 连接失败: " + failedConnections.get());

        // 发送消息
        long startTime = System.currentTimeMillis();
        CountDownLatch latch = new CountDownLatch(CLIENT_COUNT);
        for (TestWebSocketClient client : clients) {
            if (client.isConnected()) {
                Thread thread = new Thread(() -> {
                    try {
                        for (int i = 0; i < MESSAGE_PER_CLIENT; i++) {
                            client.sendMessage(createTestMessage(client.getClientId()));
                            sentMessages.incrementAndGet();
                        }
                    } catch (Exception e) {
                        System.err.println("客户端 " + client.getClientId() + " 发送消息失败: " + e.getMessage());
                    }finally {
                        latch.countDown();
                    }
                });
                thread.start();
            }
        }
        boolean await = latch.await(60, TimeUnit.SECONDS);
        if (await) {
            log.info("所有消息发送完成");
        } else {
            log.info("消息发送超时");
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // 输出统计结果
        log.info("=== 测试结果 ===");
        log.info("总耗时: {} ms", duration);
        log.info("成功连接数: {}", successConnections.get());
        log.info("发送消息数: {}", sentMessages.get());
        log.info("接收消息数: {}", receivedMessages.get());
        log.info("平均连接时间: {} ms/连接", duration / Math.max(1, successConnections.get()));

        if (successConnections.get() > 0) {
            log.info("吞吐量: {} 消息/秒", sentMessages.get() / (duration / 1000));
        }

        // 清理资源
        clients.forEach(TestWebSocketClient::close);
        log.info("测试完成");
    }

    private static MessageData createTestMessage(int clientId) {
        MatchSubMessageData subMessage = MatchSubMessageData.newBuilder()
                .setMessageCode(MatchMessageCode.CANCEL_MATCH)
                .setMessage(MatchRequest.newBuilder().setServerName(ServerName.A_SERVICE).build().toByteString())
                .build();

        return MessageData.newBuilder()
                .setUserId(String.valueOf(clientId))
                .setServerName(ServerName.MATCH_SERVICE)
                .setMessage(subMessage.toByteString())
                .build();
    }

    public static class TestWebSocketClient extends Endpoint {
        private final jakarta.websocket.WebSocketContainer container;
        private Session session;
        @Getter
        private final int clientId;
        private boolean connected = false;
        private final String wsUrl;

        public TestWebSocketClient(int clientId, String wsUrl) {
            this.clientId = clientId;
            this.wsUrl = wsUrl;
            this.container = ContainerProvider.getWebSocketContainer();
        }

        public void connect() throws Exception {
            // 生成JWT token
            Map<String, Object> claims = new HashMap<>();
            claims.put("userId", clientId);
            String token = Jwts.builder()
                    .setClaims(claims)
                    .setSubject("websocket-user")
                    .setIssuedAt(new Date(System.currentTimeMillis()))
                    .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 24))
                    .signWith(SignatureAlgorithm.HS512, "s3cr3t_k3y_w1th_m0r3_r4nd0mn3ss_f0r_w3bs0ck3t_s3rv1c3_2025")
                    .compact();

            URI uri = URI.create(wsUrl);

            // 配置ClientEndpointConfig，将token放入请求头
            ClientEndpointConfig config = ClientEndpointConfig.Builder.create()
                    .configurator(new ClientEndpointConfig.Configurator() {
                        @Override
                        public void beforeRequest(Map<String, List<String>> headers) {
                            headers.put("token", Collections.singletonList(token));
                        }
                    })
                    .build();

            // 使用配置连接到WebSocket服务器
            session = container.connectToServer(this, config, uri);
            connected = true;
        }

        @Override
        public void onOpen(Session session, EndpointConfig config) {
            log.info("客户端 " + clientId + " 连接打开");
            this.session = session;

            // 添加消息处理器
            session.addMessageHandler((MessageHandler.Whole<ByteBuffer>) message -> {
                receivedMessages.incrementAndGet();
                log.info("客户端 {} 接收到消息，大小: {} 字节", clientId, message.remaining());
            });
        }

        @Override
        public void onError(Session session, Throwable throwable) {
            System.err.println("客户端 " + clientId + " 发生错误: " + throwable.getMessage());
            connected = false;
        }

        @Override
        public void onClose(Session session, CloseReason closeReason) {
//            log.info("客户端 " + clientId + " 连接关闭: " + closeReason);
            connected = false;
        }

        public void sendMessage(MessageData messageData) throws Exception {
            if (session != null && session.isOpen()) {
                ByteBuffer buffer = ByteBuffer.wrap(messageData.toByteArray());
                session.getBasicRemote().sendBinary(buffer);
                log.info("客户端 {} 发送消息，大小: {} 字节", clientId, buffer.remaining());
            }
        }

        public void close() {
            try {
                if (session != null && session.isOpen()) {
                    session.close();
                }
            } catch (Exception e) {
                System.err.println("关闭客户端 " + clientId + " 时出错: " + e.getMessage());
            }
        }

        public boolean isConnected() {
            return connected && session != null && session.isOpen();
        }

    }
}
