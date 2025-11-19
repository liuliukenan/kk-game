package com.kkgame.util;

import com.kkgame.api.DubboApi;
import com.kkgame.enums.ServerNameEnum;
import com.kkgame.protobuf.MessageData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WsMessageUtil {

    private static final Logger log = LoggerFactory.getLogger(WsMessageUtil.class);

    /**
     * 通知单个玩家
     * @param messageData 消息数据
     */
    public static void notifyPlayer(MessageData messageData) {
        String clientId = messageData.getClientId();
        try {
            // 使用公共模块的方法处理消息
            DubboApi api = ClientApiManager.fetchClientApi(clientId, ServerNameEnum.WEBSOCKET_SERVICE.getServerName());
            if (api != null) {
                api.processMessageProto(messageData.toByteArray());
            }
        } catch (Exception e) {
            log.error("Failed to notify player: " + clientId, e);
        }
    }

    public static void clearPlayerCache(String clientId, String serverName) {
        MessageData build = MessageData.newBuilder()
                .setClientId(clientId)
                .setServerName(ServerNameEnum.fetchProtoServerName(serverName))
                .build();
        try {
            // 使用公共模块的方法处理消息
            DubboApi api = ClientApiManager.fetchClientApi(clientId, ServerNameEnum.WEBSOCKET_SERVICE.getServerName());
            if (api != null) {
                api.clearDubboApiCache(build.toByteArray());
            }
        } catch (Exception e) {
            log.error("Failed to clearPlayerCache: " + clientId, e);
        }
    }
}
