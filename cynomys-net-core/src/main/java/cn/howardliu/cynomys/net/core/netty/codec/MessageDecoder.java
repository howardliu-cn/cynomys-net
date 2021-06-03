package cn.howardliu.cynomys.net.core.netty.codec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.howardliu.cynomys.net.core.common.NetHelper;
import cn.howardliu.cynomys.net.core.domain.Header;
import cn.howardliu.cynomys.net.core.domain.Message;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.util.CharsetUtil;

/**
 * @author Howard Liu <howardliu1988@163.com>
 * Created on 2021-06-02
 */
public class MessageDecoder extends LengthFieldBasedFrameDecoder {
    private static final Logger logger = LoggerFactory.getLogger(MessageDecoder.class);

    public MessageDecoder(int maxFrameLength, int lengthFieldOffset, int lengthFieldLength) {
        super(maxFrameLength, lengthFieldOffset, lengthFieldLength);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        ByteBuf frame = null;
        try {
            frame = (ByteBuf) super.decode(ctx, in);
            if (frame == null) {
                return null;
            }
            final Header header = Header.createHeader();
            header.setCrcCode(frame.readInt());
            header.setLength(frame.readInt());
            header.setOpaque(frame.readLong());
            header.setTag(frame.readCharSequence(frame.readInt(), CharsetUtil.UTF_8).toString());
            header.setType(frame.readByte());
            header.setCode(frame.readByte());
            final Message message = new Message();
            message.setHeader(header);

            if (frame.readableBytes() > 4) {
                final int bodyLength = frame.readInt();
                final CharSequence body = frame.readCharSequence(bodyLength, CharsetUtil.UTF_8);
                message.setBody(body.toString());
            }
            return message;
        } catch (Exception e) {
            Channel channel = ctx.channel();
            logger.error("decode exception, " + NetHelper.remoteAddress(channel), e);
            NetHelper.closeChannel(channel);
        } finally {
            if (frame != null) {
                frame.release();
            }
        }
        return null;
    }
}
