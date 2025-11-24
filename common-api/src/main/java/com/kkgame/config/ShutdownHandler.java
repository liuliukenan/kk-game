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
            log.info("开始清理本服务相关数据 event:{}", event);
            ClientApiManager.clearLocalReferenceCache();
        } catch (Exception e) {
            log.error("清理Redis数据时发生错误", e);
        }
    }
}
