package cn.howardliu.cynomys.net.core.netty.codec;

import cn.howardliu.cynomys.net.core.domain.Message;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.CharsetUtil;

/**
 * @author Howard Liu <howardliu1988@163.com>
 * Created on 2021-06-02
 */
public class MessageEncoder extends MessageToByteEncoder<Message> {
    @Override
    protected void encode(ChannelHandlerContext ctx, Message msg, ByteBuf out) {
        if (msg == null || msg.getHeader() == null) {
            throw new IllegalArgumentException("the encode message is null.");
        }
        out.writeInt(msg.getHeader().getCrcCode());
        out.writeInt(msg.getHeader().getLength());
        out.writeLong(msg.getHeader().getOpaque());

        out.writeInt(msg.getHeader().getTag().length());
        out.writeCharSequence(msg.getHeader().getTag(), CharsetUtil.UTF_8);

        out.writeByte(msg.getHeader().getType());
        out.writeByte(msg.getHeader().getCode());

        if (msg.getBody() == null) {
            out.writeInt(0);
        } else {
            out.writeInt(msg.getBody().getBytes().length);
            out.writeCharSequence(msg.getBody(), CharsetUtil.UTF_8);
        }
        out.setInt(4, out.readableBytes() - 8);
    }
}
