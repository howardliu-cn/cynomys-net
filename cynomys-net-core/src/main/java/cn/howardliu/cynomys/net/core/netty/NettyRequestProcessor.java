package cn.howardliu.cynomys.net.core.netty;

import cn.howardliu.cynomys.net.core.domain.Message;
import io.netty.channel.ChannelHandlerContext;

/**
 * 请求处理器
 *
 * @author Howard Liu <howardliu1988@163.com>
 * Created on 2021-06-01
 */
public interface NettyRequestProcessor {
    Message processRequest(ChannelHandlerContext ctx, Message request);

    default boolean rejectRequest() {
        return false;
    }
}
