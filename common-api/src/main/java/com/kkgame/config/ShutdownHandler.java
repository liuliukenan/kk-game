package com.kkgame.config;

import com.kkgame.manager.ClientApiManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;


@Component
@Slf4j
public class ShutdownHandler implements ApplicationListener<ContextClosedEvent> {

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        try {
            log.info("开始清理Redis中所有WebSocket相关数据");
            // 调用WebSocket处理器的清理方法
            clearAllRedisDataOnShutdown();
        } catch (Exception e) {
            log.error("清理Redis数据时发生错误", e);
        }
    }

    /**
     * 程序关闭时清理所有Redis数据
     */
    public void clearAllRedisDataOnShutdown() {
        try {
            ClientApiManager.clearAllReferenceCache();
        } catch (Exception e) {
            log.error("清理Redis数据时发生错误", e);
        }
    }
}
