package cn.howardliu.cynomys.net.core.netty;

import java.util.concurrent.ThreadLocalRandom;

import lombok.Data;

/**
 * @author Howard Liu <howardliu1988@163.com>
 * Created on 2021-06-02
 */
@Data
public class NettyClientConfig {
    private final String clientName;
    private int clientWorkerThreads = 4;
    private int clientCallbackExecutorThreads = Runtime.getRuntime().availableProcessors();
    private int clientAsyncSemaphoreValue = NettyNetConfig.CLIENT_ASYNC_SEMAPHORE_VALUE;
    private int connectTimeoutMillis = 3000;
    private int socketTimeoutMillis = 20000;
    private int clientChannelMaxIdleTimeSeconds = 10;

    private int clientSocketSndBufSize = NettyNetConfig.SOCKET_SND_BUF_SIZE;
    private int clientSocketRcvBufSize = NettyNetConfig.SOCKET_RCV_BUF_SIZE;
    private int clientSocketMaxFrameLength = NettyNetConfig.SOCKET_MAX_FRAME_LENGTH;

    private boolean clientCloseSocketIfTimeout = false;

    private int relinkMaxCount = 3;
    private int relinkDelayMillis = 2_000;

    public NettyClientConfig() {
        this("Cynomys-Netty-Client-" + Long.toHexString(ThreadLocalRandom.current().nextLong() + System.currentTimeMillis()));
    }

    public NettyClientConfig(String clientName) {
        this.clientName = clientName;
    }
}
