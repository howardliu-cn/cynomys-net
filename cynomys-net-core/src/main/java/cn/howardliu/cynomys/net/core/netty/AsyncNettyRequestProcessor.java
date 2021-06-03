package cn.howardliu.cynomys.net.core.netty;

import cn.howardliu.cynomys.net.core.NetResponseCallback;
import cn.howardliu.cynomys.net.core.domain.Message;
import io.netty.channel.ChannelHandlerContext;

/**
 * 异步请求处理器
 *
 * @author Howard Liu <howardliu1988@163.com>
 * Created on 2021-06-02
 */
public interface AsyncNettyRequestProcessor extends NettyRequestProcessor {

    default void asyncProcessRequest(ChannelHandlerContext ctx, Message request, NetResponseCallback responseCallback) {
        Message response = processRequest(ctx, request);
        responseCallback.callback(response);
    }
}
