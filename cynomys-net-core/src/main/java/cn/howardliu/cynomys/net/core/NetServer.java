package cn.howardliu.cynomys.net.core;

import java.util.concurrent.ExecutorService;

import cn.howardliu.cynomys.net.core.domain.Message;
import cn.howardliu.cynomys.net.core.exception.NetSendRequestException;
import cn.howardliu.cynomys.net.core.exception.NetTimeoutException;
import cn.howardliu.cynomys.net.core.exception.NetTooMuchRequestException;
import cn.howardliu.cynomys.net.core.netty.NettyRequestProcessor;
import io.netty.channel.Channel;

/**
 * @author Howard Liu <howardliu1988@163.com>
 * Created on 2021-06-01
 */
public interface NetServer extends NetService {
    int localListenPort();

    void registerDefaultProcessor(NettyRequestProcessor processor, ExecutorService executor);

    void registerProcessor(byte requestCode, NettyRequestProcessor processor, ExecutorService executor);

    Message sync(Channel channel, Message request, long timeoutMillis)
            throws InterruptedException, NetTimeoutException, NetSendRequestException;

    void async(Channel channel, Message request, long timeoutMillis, InvokeCallback invokeCallback)
            throws InterruptedException, NetTimeoutException, NetSendRequestException, NetTooMuchRequestException;
}
