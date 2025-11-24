package com.kkgame.api;

import com.kkgame.enums.ServerNameEnum;
import com.kkgame.protobuf.MessageData;
import com.kkgame.service.WebSocketService;
import com.kkgame.manager.ClientApiManager;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@DubboService(group = "websocket-service")
@Service
public class WebSocketDubboApiImpl implements DubboApi {

    @Autowired
    private WebSocketService webSocketService;

    @Override
    public void processMessageProto(byte[] bytes) throws Exception {
        MessageData messageData = MessageData.parseFrom(bytes);
        webSocketService.pushMessageToClientProto(messageData);
    }

    @Override
    public void clearDubboApiCache(byte[] bytes) throws Exception {
        MessageData messageData = MessageData.parseFrom(bytes);
        ClientApiManager.remove(messageData.getUserId(), ServerNameEnum.getServerNameString(messageData.getServerName()));
    }

}
