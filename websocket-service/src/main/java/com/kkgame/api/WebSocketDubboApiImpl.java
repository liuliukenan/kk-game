package com.kkgame.api;

import com.kkgame.enums.ServerNameEnum;
import com.kkgame.handler.MyWebSocketHandler;
import com.kkgame.protobuf.MessageData;
import com.kkgame.util.ClientApiManager;
import org.apache.dubbo.config.annotation.DubboService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@DubboService(group = "websocket-service")
@Service
public class WebSocketDubboApiImpl implements DubboApi {

    private static final Logger log = LoggerFactory.getLogger(WebSocketDubboApiImpl.class);

    @Autowired
    private MyWebSocketHandler myWebSocketHandler;


    @Override
    public void processMessageProto(byte[] bytes) throws Exception {
        MessageData messageData = MessageData.parseFrom(bytes);
        myWebSocketHandler.pushMessageToClientProto(messageData);
    }

    @Override
    public void clearDubboApiCache(byte[] bytes) throws Exception {
        MessageData messageData = MessageData.parseFrom(bytes);
        ClientApiManager.remove(messageData.getClientId(), ServerNameEnum.getServerNameString(messageData.getServerName()));
    }

}
