package com.kkgame.util;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.kkgame.protobuf.MessageData;

public class ProtoHexUtil {

    /**
     * 将 MessageData 对象编码为 protobuf 二进制，并输出 hex
     */
    public static String toHex(String clientId, byte[] message) {
        MessageData msg = MessageData.newBuilder()
                .setMessage(ByteString.copyFrom(message))
                .build();

        byte[] bytes = msg.toByteArray();
        return bytesToHex(bytes);
    }

    /**
     * 将 hex 字符串解码为 MessageData 对象（用于调试）
     */
    public static MessageData fromHex(String hex) throws InvalidProtocolBufferException {
        byte[] bytes = hexToBytes(hex);
        return MessageData.parseFrom(bytes);
    }

    /**
     * byte[] 转 hex
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    /**
     * hex 转 byte[]
     */
    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        if (len % 2 != 0) {
            throw new IllegalArgumentException("Invalid hex string length");
        }
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i+1), 16));
        }
        return data;
    }

    public static void main(String[] args) throws Exception {
        // 生成 hex
        String hex = toHex("abc", "hello".getBytes());
        System.out.println("发送用的 HEX: " + hex);

    }
}
