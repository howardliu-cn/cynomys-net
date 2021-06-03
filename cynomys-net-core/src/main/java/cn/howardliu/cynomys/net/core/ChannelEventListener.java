package cn.howardliu.cynomys.net.core;

import io.netty.channel.Channel;

/**
 * @author Howard Liu <howardliu1988@163.com>
 * Created on 2021-06-02
 */
public interface ChannelEventListener {
    void onChannelConnect(String address, Channel channel);

    void onChannelClose(String address, Channel channel);

    void onChannelException(String address, Channel channel, Throwable cause);

    void onChannelIdle(String address, Channel channel);

    void onChannelRead(String address, Channel channel);
}
