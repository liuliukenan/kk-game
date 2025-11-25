package com.kkgame.handler;

import cn.hutool.extra.spring.SpringUtil;
import com.google.protobuf.util.JsonFormat;
import com.kkgame.enums.ServerNameEnum;
import com.kkgame.manager.ClientApiManager;
import com.kkgame.protobuf.*;
import com.kkgame.util.DubboServiceUtil;
import com.kkgame.util.MessageBuildUtil;
import com.kkgame.util.ProtobufUtil;
import com.kkgame.util.WsMessageUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 处理匹配请求的消息处理器
 */
@Slf4j
@Component
public class MatchRequestHandler implements MatchMessageHandler {


    // 存储等待匹配的玩家列表
    private final List<String> waitingPlayers = new CopyOnWriteArrayList<>();

    @Override
    public MatchMessageCode getSupportedMessageCode() {
        return MatchMessageCode.MATCH;
    }

    @Override
    public void handleMessage(String userId, MatchSubMessageData message) throws Exception {
        log.info("处理匹配请求，用户ID: {}, 请求内容: {}", userId, JsonFormat.printer().print(message));
        if (waitingPlayers.contains(userId)) {
            return;
        }
        MatchRequest matchRequest = MatchRequest.parseFrom(message.getMessage());
        waitingPlayers.add(userId);
        log.info(" 加入匹配队列 {} {} 当前人数:{}",
                userId, matchRequest.getServerName(), waitingPlayers.size());

        // 检查是否可以创建房间（每2个玩家一组）
        if (waitingPlayers.size() >= 2) {
            // 获取两个玩家
            String player1 = waitingPlayers.removeFirst();
            String player2 = waitingPlayers.removeFirst();

            matchPlayers(player1, player2, matchRequest.getServerName());
        }
    }


    // 房间ID生成器
    private final AtomicInteger roomIdGenerator = new AtomicInteger(1);

    /**
     * 匹配玩家并创建房间
     * @param player1 玩家1
     * @param player2 玩家2
     * @param serverName 服务器名称
     */
    public void matchPlayers(String player1, String player2, ServerName serverName) {
        // 创建房间
        String roomId = "room-" + roomIdGenerator.getAndIncrement();
        log.info("Creating room: {}", roomId);

        log.info("Matching players: {} and {} into room {}", player1, player2, roomId);

        try {
            // 获取一个可用的a-service实例的ip和端口
            String serverNameString = ServerNameEnum.getServerNameString(serverName);
            String ipAndPort = DubboServiceUtil.getServiceInstanceInfo(serverNameString);
            log.info("Selected a-service instance: {}", ipAndPort);

            // 将匹配信息写入Redis
            saveMatchInfoToRedis(roomId, player1, player2, ipAndPort);

            // 清除玩家原来的缓存
            WsMessageUtil.clearPlayerCache(player1, serverNameString);
            WsMessageUtil.clearPlayerCache(player2, serverNameString);

            // redis存储玩家的服务信息
            ClientApiManager.clientAndServiceToRedis(player1, serverNameString, ipAndPort);
            ClientApiManager.clientAndServiceToRedis(player2, serverNameString, ipAndPort);

            // 通知玩家匹配成功
            notifyPlayersRoomCreated(roomId, player1, player2, serverName);

        } catch (Exception e) {
            log.error("Failed to match players", e);
        }
    }

    /**
     * 通知玩家房间已创建
     *
     * @param roomId  房间ID
     * @param player1 玩家1
     * @param player2 玩家2
     * @param serverName 服务器名称
     */
    private void notifyPlayersRoomCreated(String roomId, String player1, String player2, ServerName serverName) {
        try {
            MatchResponse response1 = MatchResponse.newBuilder()
                    .setStatusMsg(ProtobufUtil.buildSuccessStatusMsg())
                    .setServerName(serverName)
                    .setRoomId(roomId)
                    .build();

            // 通知玩家1
            MessageData messageData1 = MessageBuildUtil.buildMessageData(player1, MatchMessageCode.MATCH, response1.toByteArray());
            WsMessageUtil.notifyPlayer(messageData1);

            // 通知玩家2
            MessageData messageData2 = MessageBuildUtil.buildMessageData(player2, MatchMessageCode.MATCH, response1.toByteArray());
            WsMessageUtil.notifyPlayer(messageData2);

            log.info("Notified players {} and {} about room {} creation", player1, player2, roomId);
        } catch (Exception e) {
            log.error("Failed to notify players about room creation", e);
        }
    }

    /**
     * 将匹配信息保存到Redis
     *
     * @param roomId           房间ID
     * @param player1          玩家1
     * @param player2          玩家2
     * @param aServiceInstance a-service实例信息
     */
    private void saveMatchInfoToRedis(String roomId, String player1, String player2, String aServiceInstance) {
        try {
            // 创建匹配信息的HashMap
            Map<String, String> matchInfo = new HashMap<>();
            matchInfo.put("roomId", roomId);
            matchInfo.put("player1", player1);
            matchInfo.put("player2", player2);
            matchInfo.put("aServiceInstance", aServiceInstance);
            matchInfo.put("timestamp", String.valueOf(System.currentTimeMillis()));

            // 将匹配信息存储到Redis中
            String key = "match:room:" + roomId;
            SpringUtil.getBean(StringRedisTemplate.class).opsForHash().putAll(key, matchInfo);

            log.info("Match info saved to Redis for room: {}", roomId);
        } catch (Exception e) {
            log.error("Failed to save match info to Redis for room: " + roomId, e);
        }
    }
}
