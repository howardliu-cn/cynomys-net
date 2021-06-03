package cn.howardliu.cynomys.net.core;

import cn.howardliu.cynomys.net.core.common.NetHelper;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Howard Liu <howardliu1988@163.com>
 * Created on 2021-06-02
 */
@Slf4j
public class SampleChannelEventListener implements ChannelEventListener {
    @Override
    public void onChannelConnect(String remoteAddress, Channel channel) {
        if (log.isDebugEnabled()) {
            log.debug("got CONNECT event, the remote address is {}, the local address is {}",
                    remoteAddress, NetHelper.localAddress(channel));
        }
    }

    @Override
    public void onChannelClose(String remoteAddress, Channel channel) {
        if (log.isDebugEnabled()) {
            log.debug("got CLOSE event, the remote address is {}, the local address is {}",
                    remoteAddress, NetHelper.localAddress(channel));
        }
    }

    @Override
    public void onChannelException(String address, Channel channel, Throwable cause) {
        log.warn("got EXCEPTION event, the remote address is {}, the local address is {}",
                address, NetHelper.localAddress(channel), cause);
    }

    @Override
    public void onChannelIdle(String address, Channel channel) {
        if (log.isTraceEnabled()) {
            log.trace("got IDLE event, the remote address is {}, the local address is {}",
                    address, NetHelper.localAddress(channel));
        }
    }

    @Override
    public void onChannelRead(String address, Channel channel) {
        if (log.isTraceEnabled()) {
            log.trace("got READ event, the remote address is {}, the local address is {}",
                    address, NetHelper.localAddress(channel));
        }
    }
}
