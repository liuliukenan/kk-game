package com.kkgame.api;

public interface DubboApi {

    // protobuf支持的方法
    void processMessageProto(byte[] bytes);

    // 清除链接缓存
    default void clearDubboApiCache(byte[] bytes){

    }
}
