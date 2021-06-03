package cn.howardliu.cynomys.net.core.netty.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.howardliu.cynomys.net.core.domain.Header;
import cn.howardliu.cynomys.net.core.domain.Message;
import cn.howardliu.cynomys.net.core.domain.MessageCode;
import cn.howardliu.cynomys.net.core.domain.MessageType;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;

/**
 * @author Howard Liu <howardliu1988@163.com>
 * Created on 2021-06-02
 */
public abstract class HeartbeatHandler extends SimpleChannelInboundHandler<Message> {
    private static final Logger logger = LoggerFactory.getLogger(HeartbeatHandler.class);
    protected String name;

    public HeartbeatHandler(String name) {
        this.name = name;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message message) throws Exception {
        assert message != null;
        assert message.getHeader() != null;
        Header header = message.getHeader();
        if (header.getCode() == MessageCode.HEARTBEAT_REQ_MESSAGE_CODE.getCode()) {
            // handle PING single message
            pong(ctx);
        } else if (header.getCode() == MessageCode.HEARTBEAT_RESP_MESSAGE_CODE.getCode()) {
            // handle PONG single message
            if (logger.isTraceEnabled()) {
                logger.trace("{} get PONG single message from {}", name, ctx.channel().remoteAddress());
            }
        } else {
            ctx.fireChannelRead(message);
        }
    }

    protected void ping(ChannelHandlerContext ctx) {
        final Header header = customHeader();
        header.setType(MessageType.REQUEST.value());
        header.setCode(MessageCode.HEARTBEAT_REQ_MESSAGE_CODE.getCode());
        final Message message = new Message();
        message.setHeader(header);
        ctx.writeAndFlush(message);

        if (logger.isTraceEnabled()) {
            logger.trace("{} send PING single message to {}", name, ctx.channel().remoteAddress());
        }
    }

    protected void pong(ChannelHandlerContext ctx) {
        final Header header = customHeader();
        header.setType(MessageType.RESPONSE.value());
        header.setCode(MessageCode.HEARTBEAT_RESP_MESSAGE_CODE.getCode());
        final Message message = new Message();
        message.setHeader(header);
        ctx.writeAndFlush(message);

        if (logger.isDebugEnabled()) {
            logger.debug("{} send PONG single message to {}", name, ctx.channel().remoteAddress());
        }
    }

    protected abstract Header customHeader();

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object event) throws Exception {
        super.userEventTriggered(ctx, event);
        if (event instanceof IdleStateEvent) {
            IdleStateEvent e = (IdleStateEvent) event;
            switch (e.state()) {
                case READER_IDLE:
                    handleReaderIdle(ctx);
                    break;
                case WRITER_IDLE:
                    handleWriterIdle(ctx);
                    break;
                case ALL_IDLE:
                    handleAllIdle(ctx);
                    break;
                default:
                    logger.debug("default action with state: {}", e.state());
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        logger.error("got an exception", cause);
    }

    protected void handleReaderIdle(ChannelHandlerContext ctx) {
        logger.warn("READER IDLE");
    }

    protected void handleWriterIdle(ChannelHandlerContext ctx) {
        logger.warn("WRITER IDLE");
    }

    protected void handleAllIdle(ChannelHandlerContext ctx) {
        logger.warn("ALL IDLE");
    }
}
