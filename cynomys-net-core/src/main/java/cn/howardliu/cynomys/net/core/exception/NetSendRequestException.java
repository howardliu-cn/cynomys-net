package cn.howardliu.cynomys.net.core.exception;

/**
 * @author Howard Liu <howardliu1988@163.com>
 * Created on 2021-06-02
 */
public class NetSendRequestException extends NetException {
    public NetSendRequestException(String address) {
        this(address, null);
    }

    public NetSendRequestException(String address, Throwable cause) {
        super("send request to <" + address + "> failed!", cause);
    }
}
