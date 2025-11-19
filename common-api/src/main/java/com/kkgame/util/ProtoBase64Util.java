package com.kkgame.util;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.kkgame.protobuf.*;

import java.util.Base64;

public class ProtoBase64Util {

    /**
     * 将 MessageData 对象编码为 Base64 字符串
     */
    public static String toBase64(byte[] message, ServerName serverName) {
        MessageData msg = MessageData.newBuilder()
                .setServerName(serverName)
                .setMessage(ByteString.copyFrom(message))
                .build();
        byte[] bytes = msg.toByteArray();
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * 将 Base64 字符串解码为 MessageData 对象
     */
    public static MessageData fromBase64(String base64) throws InvalidProtocolBufferException {
        byte[] bytes = Base64.getDecoder().decode(base64);
        return MessageData.parseFrom(bytes);
    }

    public static void main(String[] args) throws Exception {
        // 编码示例
        // match-service: EAMaBggBEgIIAg==
        // a-service: EAIaCAgBEgQIARAB
        MatchSubMessageData subMessageData = MatchSubMessageData.newBuilder()
                .setMessageCode(MatchMessageCode.MATCH)
                .setMessage(
                        MatchRequest.newBuilder().setServerName(ServerName.A_SERVICE).build().toByteString())
                .build();
        String base641 = toBase64(subMessageData.toByteArray(), ServerName.MATCH_SERVICE);
        System.out.println("发送用的 Base64: " + base641);


        ASubMessageData aSubMessageData = ASubMessageData.newBuilder()
                .setMessageCode(AMessageCode.SIT_DOWN)
                .setMessage(
                        SitDownRequest.newBuilder().setGameId(1).setRoomId(1).build().toByteString())
                .build();
        String base642 = toBase64(aSubMessageData.toByteArray(), ServerName.A_SERVICE);
        System.out.println("发送用的 Base64: " + base642);
    }
}
