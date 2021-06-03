package cn.howardliu.cynomys.net.core.netty;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import cn.howardliu.cynomys.net.core.ChannelEventListener;
import cn.howardliu.cynomys.net.core.InvokeCallback;
import cn.howardliu.cynomys.net.core.NetResponseCallback;
import cn.howardliu.cynomys.net.core.NetService;
import cn.howardliu.cynomys.net.core.common.NetHelper;
import cn.howardliu.cynomys.net.core.common.Pair;
import cn.howardliu.cynomys.net.core.common.SemaphoreReleaseOnlyOnce;
import cn.howardliu.cynomys.net.core.common.ServiceThread;
import cn.howardliu.cynomys.net.core.domain.Message;
import cn.howardliu.cynomys.net.core.domain.MessageType;
import cn.howardliu.cynomys.net.core.exception.NetSendRequestException;
import cn.howardliu.cynomys.net.core.exception.NetTimeoutException;
import cn.howardliu.cynomys.net.core.exception.NetTooMuchRequestException;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Howard Liu <howardliu1988@163.com>
 * Created on 2021-06-02
 */
@Slf4j
public abstract class NettyNetAbstract implements NetService {

    /**
     * 控制最大异步请求数的信号量，用于保护系统内存占用
     */
    protected final Semaphore semaphoreAsync;

    /**
     * 存储所有请求的Map[opaque, ResponseFuture]
     */
    protected final ConcurrentMap<Long, ResponseFuture> responseTable = new ConcurrentHashMap<>(256);

    /**
     * 所有请求code对应的处理器
     */
    protected final Map<Byte, Pair<NettyRequestProcessor, ExecutorService>> processorTable = new HashMap<>(64);

    /**
     * 通过{@link ChannelEventListener}处理{@link NettyEvent}的处理器
     */
    protected final NettyEventExecutor nettyEventExecutor = new NettyEventExecutor();

    /**
     * {@link #processorTable}中没有定义的默认请求对应处理器
     */
    protected Pair<NettyRequestProcessor, ExecutorService> defaultRequestProcessor;

    /**
     * 需要指明请求最大处理量
     *
     * @param permitsAsync 请求最大处理量
     */
    protected NettyNetAbstract(final int permitsAsync) {
        this.semaphoreAsync = new Semaphore(permitsAsync, true);
    }

    /**
     * 获取ChannelEventListener实例，由子类实现，可以为null。
     *
     * @return ChannelEventListener实例 或 null
     */
    public abstract ChannelEventListener getChannelEventListener();

    public Message invokeSync(final Channel channel, final Message request, final long timeoutMillis)
            throws InterruptedException, NetTimeoutException, NetSendRequestException {
        final long opaque = request.getHeader().getOpaque();
        try {
            final ResponseFuture responseFuture = new ResponseFuture(channel, opaque, timeoutMillis, null, null);
            this.responseTable.put(opaque, responseFuture);
            channel.writeAndFlush(request)
                    .addListener((ChannelFutureListener) future -> {
                        if (future.isSuccess()) {
                            responseFuture.setSendRequestOK(true);
                            return;
                        } else {
                            responseFuture.setSendRequestOK(false);
                        }
                        responseTable.remove(opaque);
                        responseFuture.setCause(future.cause());
                        responseFuture.putResponse(null);
                        log.warn("send a request to channel <{}> failed!", NetHelper.remoteAddress(channel));
                    });
            final Message response = responseFuture.waitResponse();
            if (Objects.isNull(response)) {
                if (responseFuture.isSendRequestOK()) {
                    throw new NetTimeoutException(NetHelper.remoteAddress(channel), timeoutMillis,
                            responseFuture.getCause());
                } else {
                    throw new NetSendRequestException(NetHelper.remoteAddress(channel), responseFuture.getCause());
                }
            }
            return response;
        } finally {
            responseTable.remove(opaque);
        }
    }

    public void invokeAsync(final Channel channel, final Message request, final long timeoutMillis,
            final InvokeCallback invokeCallBack)
            throws InterruptedException, NetTooMuchRequestException, NetTimeoutException, NetSendRequestException {
        final long beginStartTime = System.currentTimeMillis();
        final long opaque = request.getHeader().getOpaque();
        boolean acquired = this.semaphoreAsync.tryAcquire(timeoutMillis, TimeUnit.MILLISECONDS);
        if (acquired) {
            final SemaphoreReleaseOnlyOnce once = new SemaphoreReleaseOnlyOnce(this.semaphoreAsync);
            final long costTime = System.currentTimeMillis() - beginStartTime;
            if (timeoutMillis < costTime) {
                once.release();
                throw new NetTimeoutException("invokeAsyncImpl call timeout");
            }

            final ResponseFuture responseFuture =
                    new ResponseFuture(channel, opaque, timeoutMillis, invokeCallBack, once);
            this.responseTable.put(opaque, responseFuture);
            try {
                channel.writeAndFlush(request)
                        .addListener((ChannelFutureListener) future -> {
                            if (future.isSuccess()) {
                                responseFuture.setSendRequestOK(true);
                                return;
                            }
                            requestFail(opaque);
                            log.warn("send a request to channel <{}> failed! this channel status is {}",
                                    NetHelper.remoteAddress(channel), channel.isActive(), future.cause());
                        });
            } catch (Exception e) {
                responseFuture.release();
                String remoteAddress = NetHelper.remoteAddress(channel);
                log.warn("send a request to channel <{}> exception", remoteAddress, e);
                throw new NetSendRequestException(remoteAddress, e);
            }
        } else {
            if (timeoutMillis <= 0) {
                throw new NetTooMuchRequestException("invokeAsyncImpl invoke too fast");
            } else {
                String cause = "invokeAsync tryAcquire semaphore timeout, " + timeoutMillis + "ms, "
                        + "waiting thread numbers: " + this.semaphoreAsync.getQueueLength()
                        + " semaphoreAsyncValue: " + this.semaphoreAsync.availablePermits();
                log.warn(cause);
                throw new NetTooMuchRequestException(cause);
            }
        }
    }

    private void requestFail(final long opaque) {
        final ResponseFuture responseFuture = responseTable.remove(opaque);
        if (responseFuture == null) {
            return;
        }
        responseFuture.setSendRequestOK(false);
        responseFuture.putResponse(null);

        try {
            executeInvokeCallback(responseFuture);
        } catch (Throwable e) {
            log.warn("execute callback in writeAndFlush addListener, and callback throw", e);
        } finally {
            responseFuture.release();
        }
    }

    /**
     * 当channel关闭时，快速结束请求处理并立马返回失败响应，避免无效请求处理。
     *
     * @param channel 已经关闭的channel
     */
    protected void failFast(final Channel channel) {
        for (Entry<Long, ResponseFuture> entry : responseTable.entrySet()) {
            if (entry.getValue().getProcessChannel() == channel) {
                Long opaque = entry.getKey();
                if (opaque != null) {
                    requestFail(opaque);
                }
            }
        }
    }

    /**
     * 向{@link NettyEvent}处理器中添加一个事件对象
     *
     * @param event {@link NettyEvent}事件对象
     */
    public void putNettyEvent(final NettyEvent event) {
        this.nettyEventExecutor.putNettyEvent(event);
    }

    /**
     * 定期处理请求，并释放过期请求
     */
    public void scanResponseTable() {
        final List<ResponseFuture> rfList = new LinkedList<>();
        final Iterator<Entry<Long, ResponseFuture>> it = this.responseTable.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, ResponseFuture> next = it.next();
            ResponseFuture response = next.getValue();
            if (response.getBeginTimestamp() + response.getTimeoutMillis() + 1000 <= System.currentTimeMillis()) {
                response.release();
                it.remove();
                rfList.add(response);
                log.info("remove timeout request, " + response);
            }
        }

        for (ResponseFuture rf : rfList) {
            try {
                executeInvokeCallback(rf);
            } catch (Throwable e) {
                log.warn("scanResponseTable, operationComplete Exception", e);
            }
        }
    }

    public void processMessageReceived(ChannelHandlerContext ctx, Message msg) {
        if (msg == null || msg.getHeader() == null) {
            return;
        }
        final byte type = msg.getHeader().getType();
        if (type == MessageType.REQUEST.value()) {
            processRequest(ctx, msg);
        } else if (type == MessageType.RESPONSE.value()) {
            processResponse(ctx, msg);
        }
    }


    public void processRequest(ChannelHandlerContext ctx, Message request) {
        final Pair<NettyRequestProcessor, ExecutorService> matched =
                this.processorTable.get(request.getHeader().getCode());
        final Pair<NettyRequestProcessor, ExecutorService> pair =
                matched == null ? this.defaultRequestProcessor : matched;
        final long opaque = request.getHeader().getOpaque();

        if (pair == null) {
            String error = "request code " + request.getHeader().getCode() + " not supported!";

            final Message message = Message.createResponse(opaque, error);
            ctx.writeAndFlush(message);
            log.warn(NetHelper.remoteAddress(ctx.channel()) + ' ' + error);
            return;
        }

        final Runnable runnable = () -> {
            try {
                final NetResponseCallback callback = (response) -> {
                    if (Objects.isNull(response)) {
                        return;
                    }
                    response.getHeader().setOpaque(opaque);
                    response.getHeader().setType(MessageType.RESPONSE.value());
                    try {
                        ctx.writeAndFlush(response);
                    } catch (Throwable t) {
                        log.error("process request over, but response failed", t);
                        log.error(request.toString());
                        log.error(response.toString());
                    }
                };

                if (pair.getLeft() instanceof AsyncNettyRequestProcessor) {
                    final AsyncNettyRequestProcessor processor = (AsyncNettyRequestProcessor) pair.getLeft();
                    processor.asyncProcessRequest(ctx, request, callback);
                } else {
                    final NettyRequestProcessor processor = pair.getLeft();
                    final Message response = processor.processRequest(ctx, request);
                    callback.callback(response);
                }
            } catch (Throwable t) {
                final Message message = Message.createResponse(opaque, NetHelper.exceptionSimpleDesc(t));
                ctx.writeAndFlush(message);
            }
        };

        if (pair.getLeft().rejectRequest()) {
            final Message message = Message.createResponse(opaque, "system busy, start flow control for a while");
            ctx.writeAndFlush(message);
            return;
        }

        try {
            pair.getRight().submit(new NetTask(runnable, ctx.channel(), request));
        } catch (RejectedExecutionException e) {
            log.warn("{}, too many requests and system thread pool busy, {}, request code : {}",
                    NetHelper.remoteAddress(ctx.channel()), pair.getRight().toString(),
                    request.getHeader().getCode());

            final Message message = Message.createResponse(opaque, "system busy, start flow control for a while");
            ctx.writeAndFlush(message);
        }
    }

    public void processResponse(ChannelHandlerContext ctx, Message response) {
        final long opaque = response.getHeader().getOpaque();
        final ResponseFuture responseFuture = responseTable.get(opaque);
        if (responseFuture != null) {
            responseFuture.setResponse(response);
            responseTable.remove(opaque);

            if (responseFuture.getInvokeCallBack() != null) {
                executeInvokeCallback(responseFuture);
            } else {
                responseFuture.putResponse(response);
                responseFuture.release();
            }
        } else {
            log.debug("receive response, but not matched any request, remoteAddress: {}, response: {}",
                    NetHelper.remoteAddress(ctx.channel()), response);
        }
    }

    private void executeInvokeCallback(final ResponseFuture responseFuture) {
        boolean runThisThread = false;
        final ExecutorService executor = this.getCallbackExecutor();
        if (executor != null) {
            try {
                executor.submit(() -> {
                    try {
                        responseFuture.executeInvokeCallback();
                    } catch (Throwable e) {
                        log.warn("execute callback in executor exception, and callback throw", e);
                    } finally {
                        responseFuture.release();
                    }
                });
            } catch (Exception e) {
                runThisThread = true;
                log.warn("execute callback in executor exception, maybe executor busy", e);
            }
        } else {
            runThisThread = true;
        }

        if (runThisThread) {
            try {
                responseFuture.executeInvokeCallback();
            } catch (Throwable e) {
                log.warn("executeInvokeCallback Exception", e);
            } finally {
                responseFuture.release();
            }
        }
    }

    /**
     * 指明处理回调方法的线程池
     *
     * @return 线程池；或者null（使用netty的event-loop）
     */
    public abstract ExecutorService getCallbackExecutor();

    class NettyEventExecutor extends ServiceThread {
        private static final int MAX_SIZE = 1024;
        private static final int READ_FROM_QUEUE_LIMIT_MILLIS = 3000;
        private final LinkedBlockingQueue<NettyEvent> eventQueue = new LinkedBlockingQueue<>();

        void putNettyEvent(final NettyEvent event) {
            if (this.eventQueue.size() <= MAX_SIZE) {
                this.eventQueue.add(event);
            } else {
                log.info("event queue size[{}] enough, so drop this event {}",
                        this.eventQueue.size(), event.toString());
            }
        }

        @Override
        public void run() {
            log.info("{} service started", this.getServiceName());
            final ChannelEventListener listener = NettyNetAbstract.this.getChannelEventListener();

            while (!this.isStopped()) {
                try {
                    final NettyEvent event = this.eventQueue.poll(READ_FROM_QUEUE_LIMIT_MILLIS, TimeUnit.MILLISECONDS);
                    if (event == null || listener == null) {
                        continue;
                    }
                    switch (event.getType()) {
                        case IDLE:
                            listener.onChannelIdle(event.getRemoteAddress(), event.getChannel());
                            break;
                        case CLOSE:
                            listener.onChannelClose(event.getRemoteAddress(), event.getChannel());
                            break;
                        case CONNECT:
                            listener.onChannelConnect(event.getRemoteAddress(), event.getChannel());
                            break;
                        case EXCEPTION:
                            listener.onChannelException(event.getRemoteAddress(), event.getChannel(), event.getCause());
                            break;
                        case READ:
                            listener.onChannelRead(event.getRemoteAddress(), event.getChannel());
                        default:
                            break;
                    }
                } catch (Exception e) {
                    log.warn("{} service has exception. ", this.getServiceName(), e);
                }
            }
            log.info("{} service end", this.getServiceName());
        }

        @Override
        public String getServiceName() {
            return NettyEventExecutor.class.getSimpleName();
        }
    }
}
