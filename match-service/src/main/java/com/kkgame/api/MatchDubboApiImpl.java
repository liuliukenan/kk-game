package com.kkgame.api;

import com.google.protobuf.ByteString;
import com.kkgame.handler.MatchMessageHandler;
import com.kkgame.protobuf.MatchMessageCode;
import com.kkgame.protobuf.MatchSubMessageData;
import com.kkgame.protobuf.MessageData;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Map;

@DubboService(group = "match-service")
@Service
@Slf4j
public class MatchDubboApiImpl implements DubboApi {

    @Resource
    Map<MatchMessageCode, MatchMessageHandler> messageHandlerMap;

    @Override
    public void processMessageProto(byte[] bytes) {
        try {
            MessageData messageData = MessageData.parseFrom(bytes);
            String clientId = messageData.getUserId();
            MatchSubMessageData subMessageData = MatchSubMessageData.parseFrom(messageData.getMessage());
    //        log.info("收到消息 clientId: {} messageData: {}", clientId, JsonFormat.printer().print(messageData));
    //        log.info("收到消息 clientId: {} subMessageData: {}", clientId, JsonFormat.printer().print(subMessageData));

            MatchMessageCode messageCode = subMessageData.getMessageCode();

            // 使用命令模式处理消息
            MatchMessageHandler handler = messageHandlerMap.get(messageCode);
            if (handler != null) {
                handler.handleMessage(clientId, subMessageData);
            } else {
                log.warn("未找到消息处理器 clientId: {} messageCode: {}", clientId, messageCode);
            }
        } catch (Exception e) {
            log.error("处理消息失败 bytes: {}", ByteString.copyFrom(bytes), e);
        }
    }
}
