package cn.howardliu.cynomys.net.core.domain;

import java.util.Objects;

import cn.howardliu.cynomys.net.core.BaseInfo;
import lombok.Data;

/**
 * 传输消息
 *
 * @author Howard Liu <howardliu1988@163.com>
 * Created on 2021-06-01
 * @see Message
 * @see MessageType
 */
@Data
public class Header {
    /**
     * 请求序列号
     */
    private long opaque;
    /**
     * 校验码，用于验证客户端与服务端之间消息是否能够彼此处理
     */
    private int crcCode = BaseInfo.CURRENT_VERSION;
    /**
     * 终端标识
     */
    private String tag = BaseInfo.CURRENT_TAG;
    /**
     * 消息长度
     */
    private int length;
    /**
     * 消息类型：请求、响应
     *
     * @see MessageType
     */
    private byte type;
    /**
     * 消息类型编号
     *
     * @see MessageCode
     */
    private byte code;

    protected Header() {
        BaseInfo.REQUEST_SEQ.increment();
        this.opaque = BaseInfo.REQUEST_SEQ.longValue();
    }

    public static Header createHeader() {
        return new Header();
    }

    public void setCode(byte code) {
        this.code = code;
    }

    public void setCode(MessageCode mc) {
        setCode(Objects.requireNonNull(mc).getCode());
    }
}
