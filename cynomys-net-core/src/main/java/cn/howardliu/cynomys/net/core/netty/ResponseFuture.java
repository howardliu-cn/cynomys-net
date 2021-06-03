package cn.howardliu.cynomys.net.core.netty;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import cn.howardliu.cynomys.net.core.InvokeCallback;
import cn.howardliu.cynomys.net.core.common.SemaphoreReleaseOnlyOnce;
import cn.howardliu.cynomys.net.core.domain.Message;
import io.netty.channel.Channel;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Howard Liu <howardliu1988@163.com>
 * Created on 2021-06-02
 */
@Data
@Slf4j
public class ResponseFuture {
    private final long opaque;
    private final Channel processChannel;
    private final long timeoutMillis;
    private final InvokeCallback invokeCallBack;
    private final long beginTimestamp = System.currentTimeMillis();
    private final SemaphoreReleaseOnlyOnce releaseSemaphore;
    private final CountDownLatch countDownLatch = new CountDownLatch(1);

    private Message response;
    private Throwable cause;
    private volatile boolean sendRequestOK;
    private volatile boolean timeout = false;

    public ResponseFuture(Channel channel, long opaque, long timeoutMillis, InvokeCallback invokeCallBack,
            SemaphoreReleaseOnlyOnce once) {
        this.processChannel = channel;
        this.opaque = opaque;
        this.timeoutMillis = timeoutMillis;
        this.invokeCallBack = invokeCallBack;
        this.releaseSemaphore = once;
    }

    public void executeInvokeCallback() {
        if (invokeCallBack != null) {
            invokeCallBack.operationComplete(this);
        }
    }

    public void release() {
        if (this.releaseSemaphore != null) {
            this.releaseSemaphore.release();
        }
    }

    public boolean isTimeout() {
        if (timeout) {
            return timeout;
        }
        timeout = System.currentTimeMillis() - this.beginTimestamp > this.timeoutMillis;
        return timeout;
    }

    public Message waitResponse() throws InterruptedException {
        this.waitResponse(this.timeoutMillis);
        return this.response;
    }

    private void waitResponse(long waitMills) throws InterruptedException {
        if (!this.countDownLatch.await(waitMills, TimeUnit.MILLISECONDS)) {
            log.debug("timeout({}ms) when waiting for response", waitMills);
        }
    }

    public void putResponse(final Message message) {
        this.response = message;
        this.countDownLatch.countDown();
    }
}
