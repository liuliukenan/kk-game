package com.kkgame.constans;

public class RedisKeyUtil {

    /**
     * 用户服务关系缓存
     * 绑定用户ID与服务名称
     */
    private static final String USER_SERVER_KEY = "user:server:";

    /**
     * 获取用户服务关系缓存的key
     * @param serverName 服务名称
     * @return key
     */
    public static String fetchUserServerKey(String serverName) {
        return USER_SERVER_KEY + serverName;
    }
}
