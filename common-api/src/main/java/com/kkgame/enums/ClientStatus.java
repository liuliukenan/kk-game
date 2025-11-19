package com.kkgame.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ClientStatus {

    SUCCESS(200, "成功"),
    ERROR(500, "系统异常"),
    ;
    private final int code;
    private final String msg;
}
