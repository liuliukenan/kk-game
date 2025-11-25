package com.kkgame.handler;

import com.google.protobuf.Message;
import com.kkgame.protobuf.MatchMessageCode;
import com.kkgame.protobuf.MatchRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 处理取消匹配请求的消息处理器
 */
@Slf4j
@Component
public class CancelMatchHandler implements MatchMessageHandler {

    @Override
    public MatchMessageCode getSupportedMessageCode() {
        return MatchMessageCode.CANCEL_MATCH;
    }

    @Override
    public Message getDefaultMessageInstance() {
        return MatchRequest.getDefaultInstance();
    }

    @Override
    public void handleMessage(String userId, Message message) {
        log.warn("玩家 {} 取消了匹配", userId);
    }
}
