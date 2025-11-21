package com.kkgame.api;

import com.kkgame.protobuf.MessageData;
import com.kkgame.service.WebSocketService;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@DubboService(group = "websocket-service1")
@Service
public class WebSocketDubboApiImpl2 {


    @Autowired
    private WebSocketService webSocketService;


    public void processMessageProto(byte[] bytes) throws Exception {
        MessageData messageData = MessageData.parseFrom(bytes);
        webSocketService.pushMessageToClientProto(messageData);
    }


}
