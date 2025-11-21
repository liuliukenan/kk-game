package com.kkgame.util;

import cn.hutool.extra.spring.SpringUtil;
import org.springframework.cloud.client.serviceregistry.Registration;

public class CommonUtil {

    // 获取当前服务名称
    public static String fetchLocalServerName() {
        return SpringUtil.getProperty("spring.application.name");
    }

    /**
     * 生成服务器ID
     * @return 服务器ID (格式: IP:端口)
     */
    public static String fetchLocalServerId() {
        Registration bean = SpringUtil.getBean(Registration.class);
        return bean.getHost() + ":" + bean.getPort();
    }
}
