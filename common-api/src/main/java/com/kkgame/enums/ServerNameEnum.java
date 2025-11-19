package com.kkgame.enums;

import com.kkgame.protobuf.ServerName;
import lombok.Getter;


@Getter
public enum ServerNameEnum {

    NONE("NONE", 0),
    WEBSOCKET_SERVICE("websocket-service", 1),
    A_SERVICE("a-service", 2),
    MATCH_SERVICE("match-service", 3),
    ;

    private final String serverName;
    private final Integer code;

    ServerNameEnum(String serverName, Integer code) {
        this.serverName = serverName;
        this.code = code;
    }

    // 获取服务名
    public static String getServerNameString(ServerName serverName) {
        for (ServerNameEnum value : values()) {
            if (value.code == serverName.getNumber()) {
                return value.serverName;
            }
        }
        return NONE.serverName;
    }

    public static ServerNameEnum fetchServerNameEnum(String serverName) {
        for (ServerNameEnum value : values()) {
            if (value.serverName.equals(serverName)) {
                return value;
            }
        }
        return NONE;
    }

    public static ServerName fetchProtoServerName(String serverName) {
        ServerNameEnum serverNameEnum = fetchServerNameEnum(serverName);
        for (ServerName value : ServerName.values()) {
            if (value.getNumber() == serverNameEnum.code) {
                return value;
            }
        }
        return ServerName.NONE;
    }

}
