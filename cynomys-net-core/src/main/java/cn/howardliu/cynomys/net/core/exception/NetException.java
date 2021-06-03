package cn.howardliu.cynomys.net.core.exception;

/**
 * @author Howard Liu <howardliu1988@163.com>
 * Created on 2021-06-02
 */
public abstract class NetException extends Exception {
    protected NetException(String message) {
        super(message);
    }

    protected NetException(String message, Throwable cause) {
        super(message, cause);
    }
}
