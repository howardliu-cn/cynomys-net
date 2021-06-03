package cn.howardliu.cynomys.net.core.domain;

/**
 * 请求类型
 *
 * @author Howard Liu <howardliu1988@163.com>
 * Created on 2021-06-02
 */
public enum MessageType {
    REQUEST((byte) 0),
    RESPONSE((byte) 1);

    private final byte value;

    MessageType(byte value) {
        this.value = value;
    }

    public byte value() {
        return this.value;
    }
}
