package com.kkgame.api;

import com.google.protobuf.ByteString;
import com.google.protobuf.util.JsonFormat;
import com.kkgame.protobuf.*;
import com.kkgame.util.MessageBuildUtil;
import com.kkgame.util.ProtobufUtil;
import com.kkgame.util.WsMessageUtil;
import org.apache.dubbo.config.annotation.DubboService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@DubboService(group = "a-service")
@Service
public class ADubboApiImpl implements DubboApi {

    private static final Logger log = LoggerFactory.getLogger(ADubboApiImpl.class);

    @Override
    public void processMessageProto(byte[] bytes) throws Exception {
        MessageData messageData = MessageData.parseFrom(bytes);
        ByteString message = messageData.getMessage();
        String clientId = messageData.getClientId();
        ASubMessageData subMessageData = ASubMessageData.parseFrom(message);
        SitDownRequest sitDownRequest = SitDownRequest.parseFrom(subMessageData.getMessage());
        log.info("收到消息 clientId: {} messageData: {}", clientId, JsonFormat.printer().print(messageData));
        log.info("收到消息 clientId: {} subMessageData: {}", clientId, JsonFormat.printer().print(subMessageData));
        log.info("收到消息 clientId: {} sitDownRequest: {}", clientId, JsonFormat.printer().print(sitDownRequest));


        SitDownResponse response = SitDownResponse.newBuilder()
                .setStatusMsg(ProtobufUtil.buildSuccessStatusMsg())
                .setGameId(1)
                .setRoomId(1)
                .build();
        MessageData messageData1 = MessageBuildUtil.buildMessageData(clientId, AMessageCode.SIT_DOWN, response.toByteArray());
        log.info("返回消息 clientId: {} response: {}", clientId, JsonFormat.printer().print(response));
        WsMessageUtil.notifyPlayer(messageData1);

    }

}
