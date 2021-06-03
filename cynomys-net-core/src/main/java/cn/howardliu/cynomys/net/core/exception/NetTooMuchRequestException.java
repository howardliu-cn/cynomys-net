package cn.howardliu.cynomys.net.core.exception;

/**
 * @author Howard Liu <howardliu1988@163.com>
 * Created on 2021-06-02
 */
public class NetTooMuchRequestException extends NetException {
    public NetTooMuchRequestException(String message) {
        super(message);
    }
}
