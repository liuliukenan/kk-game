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
import java.util.concurrent.ConcurrentHashMap;

@DubboService(group = "match-service")
@Service
@Slf4j
public class MatchDubboApiImpl implements DubboApi {

    @Resource
    Map<MatchMessageCode, MatchMessageHandler> messageHandlerMap;

    private static final Map<String, Thread> threadMap = new ConcurrentHashMap<>();

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
                Thread thread = Thread.currentThread();
                threadMap.put(thread.getName(), thread);
                threadMap.forEach((k, v) -> {
                    log.info("线程: {} 状态: {} 是否存活: {}",
                            v.getName(),
                            v.getState(),
                            v.isAlive());
                });

                log.info("处理消息 clientId: {} messageCode: {} thread:{}", clientId, messageCode, Thread.currentThread().getName());
//                handler.handleMessage(clientId, subMessageData);
//                log.info("处理消息完成 clientId: {} messageCode: {}", clientId, messageCode);
            } else {
                log.warn("未找到消息处理器 clientId: {} messageCode: {}", clientId, messageCode);
            }
        } catch (Exception e) {
            log.error("处理消息失败 bytes: {}", ByteString.copyFrom(bytes), e);
        }
    }
}
