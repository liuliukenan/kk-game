package com.kkgame.config;

import com.kkgame.constans.RedisKeyUtil;
import com.kkgame.util.ClientApiManager;
import com.kkgame.util.CommonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;


@Component
public class ShutdownHandler implements ApplicationListener<ContextClosedEvent> {

    private static final Logger log = LoggerFactory.getLogger(ShutdownHandler.class);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

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
            String serviceName = CommonUtil.fetchLocalServerName();
            log.info("开始清理 Redis中{}服务相关数据", serviceName);
            String pattern = RedisKeyUtil.USER_SERVER_KEY + serviceName;
            stringRedisTemplate.delete(pattern);
            log.info("清理完成 Redis中{}服务相关数据", serviceName);
            ClientApiManager.clearAllReferenceCache();
        } catch (Exception e) {
            log.error("清理Redis数据时发生错误", e);
        }
    }
}
