package cn.howardliu.cynomys.net.core.netty.handler;

import cn.howardliu.cynomys.net.core.domain.Header;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Howard Liu <howardliu1988@163.com>
 * Created on 2021-06-02
 */
@Slf4j
public class SimpleHeartbeatHandler extends HeartbeatHandler {

    public SimpleHeartbeatHandler(String name) {
        super(name);
    }

    @Override
    protected Header customHeader() {
        return Header.createHeader();
    }

    protected void handleReaderIdle(ChannelHandlerContext ctx) {
        log.trace("READER IDLE");
        handlerIdle(ctx);
    }

    protected void handleWriterIdle(ChannelHandlerContext ctx) {
        log.trace("WRITER IDLE");
        handlerIdle(ctx);
    }

    protected void handleAllIdle(ChannelHandlerContext ctx) {
        log.trace("ALL IDLE");
        handlerIdle(ctx);
    }

    protected void handlerIdle(ChannelHandlerContext ctx) {
        ping(ctx);
    }
}
