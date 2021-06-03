package cn.howardliu.cynomys.net.core.common;

import lombok.extern.slf4j.Slf4j;

/**
 * @author Howard Liu <howardliu1988@163.com>
 * Created on 2021-06-02
 */
@Slf4j
public abstract class ServiceThread implements Runnable {
    private static final long JOIN_TIME = 90L * 1000;
    protected final Thread thread;
    protected volatile boolean hasNotified = false;
    protected volatile boolean stopped = false;

    protected ServiceThread() {
        this.thread = new Thread(this, this.getServiceName());
    }

    public abstract String getServiceName();

    public void start() {
        this.thread.start();
    }

    public void shutdown() {
        this.shutdown(false);
    }

    public void shutdown(final boolean interrupt) {
        this.stopped = true;
        log.info("shutdown thread " + this.getServiceName() + " interrupt " + interrupt);
        synchronized (this) {
            if (!this.hasNotified) {
                this.hasNotified = true;
                this.notifyAll();
            }
        }

        try {
            if (interrupt) {
                this.thread.interrupt();
            }

            long beginTime = System.currentTimeMillis();
            this.thread.join(this.getJoinTime());
            long elapsedTime = System.currentTimeMillis() - beginTime;
            log.info("join thread " + this.getServiceName() + " elapsed time(ms) " + elapsedTime + " "
                    + this.getJoinTime());
        } catch (InterruptedException e) {
            log.error("{} interrupted", getServiceName(), e);
            Thread.currentThread().interrupt();
        }
    }

    public long getJoinTime() {
        return JOIN_TIME;
    }

    public boolean isStopped() {
        return stopped;
    }
}
