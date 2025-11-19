package com.kkgame.util;

import cn.hutool.extra.spring.SpringUtil;

public class CommonUtil {

    // 获取当前服务名称
    public static String fetchLocalServerName() {
        return SpringUtil.getProperty("spring.application.name");
    }
}
