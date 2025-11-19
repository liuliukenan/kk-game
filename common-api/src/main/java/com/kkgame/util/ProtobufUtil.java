package com.kkgame.util;

import com.kkgame.enums.ClientStatus;
import com.kkgame.protobuf.StatusMsg;

public class ProtobufUtil {

    public static StatusMsg buildStatusMsg(ClientStatus clientStatus) {
        return StatusMsg.newBuilder().setCode(clientStatus.getCode()).setMsg(clientStatus.getMsg()).build();
    }

    public static StatusMsg buildSuccessStatusMsg() {
        return StatusMsg.newBuilder().setCode(ClientStatus.SUCCESS.getCode()).setMsg(ClientStatus.SUCCESS.getMsg()).build();
    }
}
