package com.kkgame.util;

import com.google.protobuf.ByteString;
import com.kkgame.enums.ServerNameEnum;
import com.kkgame.protobuf.MatchMessageCode;
import com.kkgame.protobuf.MatchSubMessageData;
import com.kkgame.protobuf.MessageData;

public class MessageBuildUtil {

    public static MessageData buildMessageData(String clientId, MatchMessageCode messageCode, byte[] message) {
        MatchSubMessageData subMessageData = MatchSubMessageData.newBuilder()
                .setMessageCode(messageCode)
                .setMessage(ByteString.copyFrom(message)).build();
        return MessageData.newBuilder()
                .setClientId(clientId)
                .setServerName(ServerNameEnum.fetchProtoServerName(CommonUtil.fetchLocalServerName()))
                .setMessage(ByteString.copyFrom(subMessageData.toByteArray()))
                .build();
    }
}
