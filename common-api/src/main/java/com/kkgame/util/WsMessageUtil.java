package com.kkgame.util;

import com.kkgame.api.DubboApi;
import com.kkgame.enums.ServerNameEnum;
import com.kkgame.manager.ClientApiManager;
import com.kkgame.protobuf.MessageData;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WsMessageUtil {

    /**
     * 通知ws服务
     * @param messageData 消息数据
     */
    public static void notifyPlayer(MessageData messageData) {
        MessageProcessor.sendMessage(messageData.getUserId(), ServerNameEnum.WEBSOCKET_SERVICE.getServerName(), messageData);
    }

    /**
     * 同步通知ws服务清除玩家缓存
     */
    public static void clearPlayerCache(String userId, String serverName) {
        MessageData messageData = MessageData.newBuilder()
                .setUserId(userId)
                .setServerName(ServerNameEnum.fetchProtoServerName(serverName))
                .build();
        try {
            // 使用公共模块的方法处理消息
            DubboApi api = ClientApiManager.fetchClientApi(messageData.getUserId(), ServerNameEnum.WEBSOCKET_SERVICE.getServerName());
            if (api != null) {
                api.clearDubboApiCache(messageData.toByteArray());
            }
        } catch (Exception e) {
            log.error("Failed to clearPlayerCache: {}", messageData.getUserId(), e);
        }
    }
}
