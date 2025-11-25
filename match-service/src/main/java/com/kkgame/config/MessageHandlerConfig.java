package com.kkgame.config;

import com.kkgame.handler.MatchMessageHandler;
import com.kkgame.protobuf.MatchMessageCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 消息处理器配置类
 */
@Configuration
public class MessageHandlerConfig {
    
    /**
     * 创建消息处理器映射表
     * @param handlers 所有消息处理器
     * @return 消息处理器映射表
     */
    @Bean
    public Map<MatchMessageCode, MatchMessageHandler> messageHandlerMap(List<MatchMessageHandler> handlers) {
        Map<MatchMessageCode, MatchMessageHandler> handlerMap = new EnumMap<>(MatchMessageCode.class);
        for (MatchMessageHandler handler : handlers) {
            handlerMap.put(handler.getSupportedMessageCode(), handler);
        }
        return handlerMap;
    }
}