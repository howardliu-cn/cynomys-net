package cn.howardliu.cynomys.net.core.netty;

import cn.howardliu.cynomys.net.core.domain.Message;
import io.netty.channel.Channel;

/**
 * @author Howard Liu <howardliu1988@163.com>
 * Created on 2021-06-02
 */
public class NetTask implements Runnable {
    private final Runnable runnable;
    private final long createTimestamp = System.currentTimeMillis();
    private final Channel channel;
    private final Message request;
    private boolean stopRun = false;

    public NetTask(Runnable runnable, Channel channel, Message request) {
        this.runnable = runnable;
        this.channel = channel;
        this.request = request;
    }

    @Override
    public void run() {
        if (!this.stopRun) {
            this.runnable.run();
        }
    }

    public long getCreateTimestamp() {
        return createTimestamp;
    }

    public boolean isStopRun() {
        return stopRun;
    }

    public void setStopRun(boolean stopRun) {
        this.stopRun = stopRun;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        NetTask that = (NetTask) o;
        return createTimestamp == that.createTimestamp
                && stopRun == that.stopRun
                && (channel != null ? channel.equals(that.channel) : that.channel == null)
                && (request != null ? request.getHeader().getOpaque() == that.request.getHeader().getOpaque()
                                    : that.request == null);
    }

    @Override
    public int hashCode() {
        int result = runnable == null ? 0 : runnable.hashCode();
        result = 31 * result + (int) (createTimestamp ^ (createTimestamp >>> 32));
        result = 31 * result + (channel == null ? 0 : channel.hashCode());
        result = 31 * result + (request == null ? 0 : request.hashCode());
        return result;
    }
}
