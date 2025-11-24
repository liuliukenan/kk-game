package com.kkgame.enums;

import com.kkgame.protobuf.ServerName;
import lombok.Getter;


@Getter
public enum ServerNameEnum {

    NONE("NONE", 0, false),
    WEBSOCKET_SERVICE("websocket-service", 1,true),
    A_SERVICE("a-service", 2,true),
    MATCH_SERVICE("match-service", 3, false),
    ;

    private final String serverName;
    private final Integer code;
    // 是否有状态
    private final Boolean stateful;

    ServerNameEnum(String serverName, Integer code,  Boolean stateful) {
        this.serverName = serverName;
        this.code = code;
        this.stateful = stateful;
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

    public static boolean isStateful(ServerNameEnum serverName) {
        return fetchServerNameEnum(serverName.getServerName()).stateful;
    }

    public static boolean isStateful(String serverName) {
        boolean stateful1 = isStateful(fetchServerNameEnum(serverName));
        return stateful1;
    }

}
