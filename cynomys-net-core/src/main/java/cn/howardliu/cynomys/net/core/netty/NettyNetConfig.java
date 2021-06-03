package cn.howardliu.cynomys.net.core.netty;

import static java.lang.Integer.parseInt;

/**
 * @author Howard Liu <howardliu1988@163.com>
 * Created on 2021-06-02
 */
public class NettyNetConfig {
    private NettyNetConfig() {
    }

    public static final String CYNOMYS_NET_SOCKET_SND_BUF_SIZE = "cynomys.net.socket.sndbuf.size";
    public static final String CYNOMYS_NET_SOCKET_RCV_BUF_SIZE = "cynomys.net.socket.rcvbuf.size";
    public static final String CYNOMYS_NET_SOCKET_MAX_FRAME_LENGTH = "cynomys.net.socket.max.frame.length";
    public static final String CYNOMYS_NET_CLIENT_ASYNC_SEMAPHORE_VALUE = "cynomys.net.clientAsyncSemaphoreValue";

    public static final String DEFAULT_MAX_VALUE = "65535";

    public static final int CLIENT_ASYNC_SEMAPHORE_VALUE =
            parseInt(System.getProperty(CYNOMYS_NET_CLIENT_ASYNC_SEMAPHORE_VALUE, DEFAULT_MAX_VALUE));

    public static final int SOCKET_SND_BUF_SIZE =
            parseInt(System.getProperty(CYNOMYS_NET_SOCKET_SND_BUF_SIZE, DEFAULT_MAX_VALUE));
    public static final int SOCKET_RCV_BUF_SIZE =
            parseInt(System.getProperty(CYNOMYS_NET_SOCKET_RCV_BUF_SIZE, DEFAULT_MAX_VALUE));
    public static final int SOCKET_MAX_FRAME_LENGTH =
            parseInt(System.getProperty(CYNOMYS_NET_SOCKET_MAX_FRAME_LENGTH, Integer.toString((10 * 1024 * 1024))));
}
