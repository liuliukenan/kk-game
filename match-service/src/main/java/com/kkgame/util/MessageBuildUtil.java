package com.kkgame.util;

import com.google.protobuf.ByteString;
import com.kkgame.enums.ServerNameEnum;
import com.kkgame.protobuf.MatchMessageCode;
import com.kkgame.protobuf.MatchSubMessageData;
import com.kkgame.protobuf.MessageData;

public class MessageBuildUtil {

    public static MessageData buildMessageData(String userId, MatchMessageCode messageCode, byte[] message) {
        MatchSubMessageData subMessageData = MatchSubMessageData.newBuilder()
                .setMessageCode(messageCode)
                .setMessage(ByteString.copyFrom(message)).build();
        return MessageData.newBuilder()
                .setUserId(userId)
                .setServerName(ServerNameEnum.fetchProtoServerName(CommonUtil.fetchLocalServerName()))
                .setMessage(ByteString.copyFrom(subMessageData.toByteArray()))
                .build();
    }
}
