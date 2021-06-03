package cn.howardliu.cynomys.net.core.domain;

import lombok.Data;

/**
 * 传输消息
 *
 * @author Howard Liu <howardliu1988@163.com>
 * Created on 2021-06-01
 */
@Data
public class Message {
    /**
     * 消息头
     */
    private Header header;
    /**
     * 消息体，此处直接使用<code>String</code>类型，简化处理逻辑
     */
    private String body;

    public static Message createResponse(long opaque, String body) {
        final Header header = Header.createHeader();
        header.setOpaque(opaque);
        header.setType(MessageType.RESPONSE.value());
        final Message message = new Message();
        message.setHeader(header);
        message.setBody(body);
        return message;
    }
}
