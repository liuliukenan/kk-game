package com.kkgame.handler;

import com.kkgame.protobuf.*;
import com.kkgame.util.MessageBuildUtil;
import com.kkgame.util.WsMessageUtil;
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
    public void handleMessage(String userId, MatchSubMessageData message) {
        log.info("玩家 {} 取消匹配", userId);
        MessageData res = MessageBuildUtil.buildMessageData(userId, MatchMessageCode.CANCEL_MATCH,
                MatchResponse.newBuilder()
                        .setStatusMsg(
                                StatusMsg.newBuilder().setCode(0).setMsg("取消了匹配").build()
                        )
                        .setServerName(ServerName.MATCH_SERVICE)
                        .setRoomId("1").build().toByteArray());
        WsMessageUtil.notifyPlayerSync(res);
        log.info("玩家 {} 取消成功", userId);
    }
}
