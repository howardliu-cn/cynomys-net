package cn.howardliu.cynomys.net.core;

import java.util.List;
import java.util.concurrent.ExecutorService;

import cn.howardliu.cynomys.net.core.domain.Message;
import cn.howardliu.cynomys.net.core.exception.NetConnectException;
import cn.howardliu.cynomys.net.core.exception.NetSendRequestException;
import cn.howardliu.cynomys.net.core.exception.NetTimeoutException;
import cn.howardliu.cynomys.net.core.exception.NetTooMuchRequestException;

/**
 * @author Howard Liu <howardliu1988@163.com>
 * Created on 2021-06-01
 */
public interface NetClient extends NetService {
    void refreshAddressList(List<String> addresses);

    List<String> getAddressList();

    void connect() throws InterruptedException;

    void connect(String address) throws InterruptedException;

    boolean isChannelWriteable(String address);

    boolean isChannelWriteable();

    Message sync(Message request, long timeoutMillis)
            throws InterruptedException, NetConnectException, NetTimeoutException, NetSendRequestException;

    void async(Message request, long timeoutMills, InvokeCallback invokeCallBack)
            throws InterruptedException, NetConnectException, NetTooMuchRequestException, NetSendRequestException,
            NetTimeoutException;

    void setCallbackExecutor(ExecutorService callbackExecutor);

    ExecutorService getCallbackExecutor();
}
