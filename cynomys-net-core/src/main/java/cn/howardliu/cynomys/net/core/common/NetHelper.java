package cn.howardliu.cynomys.net.core.common;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Howard Liu <howardliu1988@163.com>
 * Created on 2021-06-02
 */
@Slf4j
public class NetHelper {
    private NetHelper() {
    }

    public static String localAddress(final Channel channel) {
        if (channel == null) {
            return "";
        }
        return getAddress(channel.localAddress());
    }

    public static String remoteAddress(final Channel channel) {
        if (channel == null) {
            return "";
        }
        return getAddress(channel.remoteAddress());
    }

    private static String getAddress(SocketAddress socketAddress) {
        final String address = socketAddress == null ? "" : socketAddress.toString();
        if (address.isEmpty()) {
            return "";
        } else if (address.contains("/")) {
            return address.substring(address.lastIndexOf('/') + 1);
        } else {
            return address;
        }
    }

    public static String exceptionSimpleDesc(Throwable t) {
        StringBuilder s = new StringBuilder();
        if (t != null) {
            s.append(t);
            StackTraceElement[] stackTrace = t.getStackTrace();
            if (stackTrace != null && stackTrace.length > 0) {
                s.append(", ");
                s.append(stackTrace[0].toString());
            }
        }
        return s.toString();
    }

    public static SocketAddress string2SocketAddress(final String address) {
        String[] s = address.split(":");
        return new InetSocketAddress(s[0], Integer.parseInt(s[1]));
    }

    public static void closeChannel(final Channel channel) {
        if (channel == null) {
            return;
        }
        final String remote = remoteAddress(channel);
        channel.close()
                .addListener(future -> {
                    if (future.isSuccess()) {
                        log.info("closeChannel: close the connection to remote address[{}] result: {}",
                                remote, future.isSuccess());
                    } else {
                        log.warn("closeChannel: close the connection to remote address[{}] result: {}",
                                remote, future.isSuccess(), future.cause());
                    }
                });
    }
}
