package cn.howardliu.cynomys.net.core;

import java.util.concurrent.atomic.AtomicBoolean;

import cn.howardliu.cynomys.net.core.netty.ResponseFuture;

/**
 * @author Howard Liu <howardliu1988@163.com>
 * Created on 2021-06-02
 */
public abstract class InvokeCallback {
    private final AtomicBoolean executed = new AtomicBoolean(false);

    public void operationComplete(ResponseFuture responseFuture) {
        if (this.executed.compareAndSet(false, true)) {
            operationComplete0(responseFuture);
        }
    }

    protected abstract void operationComplete0(ResponseFuture responseFuture);
}
