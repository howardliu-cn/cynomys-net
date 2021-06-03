package cn.howardliu.cynomys.net.core.netty;

import io.netty.channel.Channel;
import lombok.Data;

/**
 * @author Howard Liu <howardliu1988@163.com>
 * Created on 2021-06-02
 */
@Data
public class NettyEvent {
    private final NettyEventType type;
    private final String remoteAddress;
    private final Channel channel;
    private final Throwable cause;

    public NettyEvent(NettyEventType type, String remoteAddress, Channel channel) {
        this(type, remoteAddress, channel, null);
    }

    public NettyEvent(NettyEventType type, String remoteAddress, Channel channel, Throwable cause) {
        this.type = type;
        this.remoteAddress = remoteAddress;
        this.channel = channel;
        this.cause = cause;
    }
}
