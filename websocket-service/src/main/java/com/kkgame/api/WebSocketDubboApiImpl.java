package com.kkgame.api;

import com.kkgame.enums.ServerNameEnum;
import com.kkgame.manager.ClientApiManager;
import com.kkgame.protobuf.MessageData;
import com.kkgame.service.WebSocketService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@DubboService(group = "websocket-service")
@Service
public class WebSocketDubboApiImpl implements DubboApi {

    @Autowired
    private WebSocketService webSocketService;

    @Override
    public void processMessageProto(byte[] bytes) {
        try {
            MessageData messageData = MessageData.parseFrom(bytes);
            webSocketService.pushMessageToClientProto(messageData);
        } catch (Exception e) {
            log.error("processMessageProto error", e);
        }
    }

    @Override
    public void clearDubboApiCache(byte[] bytes) {
        try {
            MessageData messageData = MessageData.parseFrom(bytes);
            ClientApiManager.removeUserCache(messageData.getUserId(), ServerNameEnum.getServerNameString(messageData.getServerName()));
        } catch (Exception e) {
            log.error("Failed to clearPlayerCache", e);
        }
    }

}
