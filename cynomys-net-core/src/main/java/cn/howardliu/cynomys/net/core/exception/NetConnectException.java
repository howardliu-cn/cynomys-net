package cn.howardliu.cynomys.net.core.exception;

/**
 * @author Howard Liu <howardliu1988@163.com>
 * Created on 2021-06-02
 */
public class NetConnectException extends NetException{
    public NetConnectException(String address) {
        this(address, null);
    }

    public NetConnectException(String address, Throwable cause) {
        super("connect to <" + address + "> failed!", cause);
    }
}
