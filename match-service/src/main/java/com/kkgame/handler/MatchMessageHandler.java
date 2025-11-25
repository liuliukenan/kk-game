package com.kkgame.handler;

import com.kkgame.protobuf.MatchMessageCode;
import com.kkgame.protobuf.MatchSubMessageData;

/**
 * 匹配服务消息处理器接口
 */
public interface MatchMessageHandler {

    /**
     * 获取支持的消息类型
     * @return 消息类型枚举
     */
    MatchMessageCode getSupportedMessageCode();

    /**
     * 处理消息
     * @param userId 用户ID
     * @param message 消息内容
     * @throws Exception 处理异常
     */
    void handleMessage(String userId, MatchSubMessageData message) throws Exception;
}
