package cn.howardliu.cynomys.net.core.exception;

/**
 * @author Howard Liu <howardliu1988@163.com>
 * Created on 2021-06-02
 */
public class NetTimeoutException extends NetException {
    public NetTimeoutException(String message) {
        super(message);
    }

    public NetTimeoutException(String address, long timeoutMillis, Throwable cause) {
        super("wait response on the channel <" + address + "> timeout, " + timeoutMillis + "(ms)", cause);
    }
}
