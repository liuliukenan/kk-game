package com.kkgame.handler;

import com.google.protobuf.Message;
import com.kkgame.protobuf.MatchMessageCode;

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
     * 获取消息的默认实例
     * @return 消息默认实例
     */
    Message getDefaultMessageInstance();
    
    /**
     * 处理消息
     * @param userId 用户ID
     * @param message 消息内容
     * @throws Exception 处理异常
     */
    void handleMessage(String userId, Message message) throws Exception;
}