package cn.howardliu.cynomys.net.core.domain;

import lombok.Data;

/**
 * @author Howard Liu <howardliu1988@163.com>
 * Created on 2021-06-02
 */
@Data
public final class MessageCode {
    protected byte code;
    protected String name;

    public MessageCode(byte code, String name) {
        this.code = code;
        this.name = name;
    }

    public static final MessageCode HEARTBEAT_REQ_MESSAGE_CODE = new MessageCode((byte) 1, "HEARTBEAT_REQ");
    public static final MessageCode HEARTBEAT_RESP_MESSAGE_CODE = new MessageCode((byte) 2, "HEARTBEAT_RESP");
}
