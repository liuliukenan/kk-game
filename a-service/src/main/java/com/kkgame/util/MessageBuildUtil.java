package com.kkgame.util;

import cn.hutool.extra.spring.SpringUtil;
import com.google.protobuf.ByteString;
import com.kkgame.enums.ServerNameEnum;
import com.kkgame.protobuf.AMessageCode;
import com.kkgame.protobuf.ASubMessageData;
import com.kkgame.protobuf.MessageData;

public class MessageBuildUtil {

    public static MessageData buildMessageData(String userId, AMessageCode messageCode, byte[] message) {
        ASubMessageData subMessageData = ASubMessageData.newBuilder()
                .setMessageCode(messageCode)
                .setMessage(ByteString.copyFrom(message)).build();
        return MessageData.newBuilder()
                .setUserId(userId)
                .setServerName(ServerNameEnum.fetchProtoServerName(SpringUtil.getProperty("spring.application.name")))
                .setMessage(ByteString.copyFrom(subMessageData.toByteArray()))
                .build();
    }
}
