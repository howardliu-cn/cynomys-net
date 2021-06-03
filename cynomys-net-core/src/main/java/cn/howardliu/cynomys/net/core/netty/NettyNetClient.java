package cn.howardliu.cynomys.net.core.netty;

import java.net.SocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import cn.howardliu.cynomys.net.core.ChannelEventListener;
import cn.howardliu.cynomys.net.core.InvokeCallback;
import cn.howardliu.cynomys.net.core.NetClient;
import cn.howardliu.cynomys.net.core.common.NetHelper;
import cn.howardliu.cynomys.net.core.domain.Message;
import cn.howardliu.cynomys.net.core.exception.NetConnectException;
import cn.howardliu.cynomys.net.core.exception.NetSendRequestException;
import cn.howardliu.cynomys.net.core.exception.NetTimeoutException;
import cn.howardliu.cynomys.net.core.exception.NetTooMuchRequestException;
import cn.howardliu.cynomys.net.core.netty.codec.MessageDecoder;
import cn.howardliu.cynomys.net.core.netty.codec.MessageEncoder;
import cn.howardliu.cynomys.net.core.netty.handler.SimpleHeartbeatHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Howard Liu <howardliu1988@163.com>
 * Created on 2021-06-02
 */
@Slf4j
public class NettyNetClient extends NettyNetAbstract implements NetClient {
    private static final long LOCK_TIMEOUT_MILLIS = 3000;

    private final NettyClientConfig nettyClientConfig;
    private final Bootstrap bootstrap = new Bootstrap();
    private final EventLoopGroup eventLoopGroupWorker;
    private final Lock lockChannelTables = new ReentrantLock();
    private final ConcurrentMap<String, ChannelWrapper> channelTables = new ConcurrentHashMap<>();

    private final Timer timer = new Timer("scan-response-timer", true);

    private final AtomicReference<List<String>> srvAddrList = new AtomicReference<>();
    private final AtomicReference<String> srvAddrChosen = new AtomicReference<>();
    private final AtomicInteger srvIndex = new AtomicInteger(initValueIndex());
    private final Lock lockSrvChannel = new ReentrantLock();

    private final ExecutorService publicExecutor;

    /**
     * 处理响应数据的线程池
     */
    private ExecutorService callbackExecutor;
    private final ChannelEventListener channelEventListener;
    private DefaultEventExecutorGroup defaultEventExecutorGroup;

    public NettyNetClient(NettyClientConfig nettyClientConfig) {
        this(nettyClientConfig, null);
    }

    public NettyNetClient(final NettyClientConfig nettyClientConfig, final ChannelEventListener channelEventListener) {
        super(nettyClientConfig.getClientAsyncSemaphoreValue());

        this.nettyClientConfig = nettyClientConfig;
        this.channelEventListener = channelEventListener;

        int callbackThreads = nettyClientConfig.getClientCallbackExecutorThreads();
        if (callbackThreads <= 0) {
            callbackThreads = 4;
        }
        this.publicExecutor = Executors.newFixedThreadPool(
                callbackThreads,
                new ThreadFactory() {
                    private final AtomicInteger threadIndex = new AtomicInteger();

                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "netty-client-public-executor-" + this.threadIndex.incrementAndGet());
                    }
                }
        );

        this.eventLoopGroupWorker = new NioEventLoopGroup(
                1,
                new ThreadFactory() {
                    private final AtomicInteger threadIndex = new AtomicInteger();

                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "netty-client-selector-" + this.threadIndex.incrementAndGet());
                    }
                }
        );
    }

    @Override
    public void start() {
        this.defaultEventExecutorGroup = new DefaultEventExecutorGroup(
                this.nettyClientConfig.getClientWorkerThreads(),
                new ThreadFactory() {
                    private final AtomicInteger threadIndex = new AtomicInteger(0);

                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "NettyClientWorkerThread-" + threadIndex.incrementAndGet());
                    }
                }
        );

        this.bootstrap
                .group(this.eventLoopGroupWorker)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, false)
                .option(ChannelOption.SO_REUSEADDR, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, this.nettyClientConfig.getConnectTimeoutMillis())
                .option(ChannelOption.SO_SNDBUF, this.nettyClientConfig.getClientSocketSndBufSize())
                .option(ChannelOption.SO_RCVBUF, this.nettyClientConfig.getClientSocketRcvBufSize())
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline()
                                .addLast(defaultEventExecutorGroup)
                                .addLast(new ReadTimeoutHandler(60))
                                .addLast(new IdleStateHandler(0, 0,
                                        nettyClientConfig.getClientChannelMaxIdleTimeSeconds()))
                                .addLast(new MessageDecoder(nettyClientConfig.getClientSocketMaxFrameLength(), 4, 4))
                                .addLast(new MessageEncoder())
                                .addLast(new NettyConnectManageHandler())
                                .addLast(new SimpleHeartbeatHandler(nettyClientConfig.getClientName()))
                                .addLast(new NettyClientHandler())
                        ;
                    }
                });

        this.timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    NettyNetClient.this.scanResponseTable();
                } catch (Exception e) {
                    log.error("scanResponseTable exception", e);
                }
            }
        }, 3L * 1000, 1000);

        if (this.channelEventListener != null) {
            this.nettyEventExecutor.start();
        }
    }

    @Override
    public void shutdown() {
        try {
            this.timer.cancel();

            for (ChannelWrapper cw : this.channelTables.values()) {
                this.closeChannel(null, cw.getChannel());
            }

            this.channelTables.clear();

            this.eventLoopGroupWorker.shutdownGracefully();

            if (!this.nettyEventExecutor.isStopped()) {
                this.nettyEventExecutor.shutdown();
            }

            if (this.defaultEventExecutorGroup != null) {
                this.defaultEventExecutorGroup.shutdownGracefully();
            }
        } catch (Exception e) {
            log.error("Netty-Client shutdown exception, ", e);
        }

        if (this.publicExecutor != null) {
            try {
                this.publicExecutor.shutdown();
            } catch (Exception e) {
                log.error("Netty-Client shutdown exception, ", e);
            }
        }
    }

    @Override
    public Message sync(Message request, long timeoutMillis)
            throws InterruptedException, NetConnectException, NetTimeoutException, NetSendRequestException {
        long beginStartTime = System.currentTimeMillis();
        final Channel channel = this.getAndCreateUseAddressChosen();
        String remoteAddress = NetHelper.remoteAddress(channel);
        if (remoteAddress.isEmpty()) {
            remoteAddress = srvAddrChosen.get();
        }
        if (channel != null && channel.isActive()) {
            try {
                long costTime = System.currentTimeMillis() - beginStartTime;
                if (timeoutMillis < costTime) {
                    throw new NetTimeoutException("invokeSync call timeout");
                }
                return this.invokeSync(channel, request, timeoutMillis);
            } catch (NetSendRequestException e) {
                log.warn("sync: send request exception, so close the channel[{}]", remoteAddress);
                this.closeChannel(channel);
                throw e;
            } catch (NetTimeoutException e) {
                if (this.nettyClientConfig.isClientCloseSocketIfTimeout()) {
                    this.closeChannel(channel);
                    log.warn("sync: close socket because of timeout, {}ms, {}", timeoutMillis, remoteAddress);
                }
                log.warn("sync: wait response timeout exception, the channel[{}]", remoteAddress);
                throw e;
            }
        } else {
            this.closeChannel(channel);
            throw new NetConnectException(remoteAddress);
        }
    }

    @Override
    public void async(Message request, long timeoutMillis, InvokeCallback invokeCallBack)
            throws InterruptedException, NetConnectException, NetTooMuchRequestException, NetSendRequestException,
            NetTimeoutException {
        long beginStartTime = System.currentTimeMillis();
        final Channel channel = this.getAndCreateUseAddressChosen();
        String remoteAddress = NetHelper.remoteAddress(channel);
        if (channel != null && channel.isActive() && channel.isWritable()) {
            try {
                long costTime = System.currentTimeMillis() - beginStartTime;
                if (timeoutMillis < costTime) {
                    throw new NetTooMuchRequestException("invokeAsync call timeout");
                }
                this.invokeAsync(channel, request, timeoutMillis - costTime, invokeCallBack);
            } catch (NetSendRequestException e) {
                log.warn("async: send request exception, so close the channel [{}]", remoteAddress);
                this.closeChannel(channel);
                throw e;
            }
        } else {
            this.closeChannel(channel);
            throw new NetConnectException(remoteAddress);
        }
    }

    private Channel getAndCreateChannel(final String address) throws InterruptedException {
        if (address == null) {
            return getAndCreateUseAddressChosen();
        }

        ChannelWrapper cw = this.channelTables.get(address);
        if (cw != null && cw.isOK()) {
            return cw.getChannel();
        }
        return this.createChannel(address);
    }

    private Channel getAndCreateUseAddressChosen() throws InterruptedException {
        String address = this.srvAddrChosen.get();
        if (address != null) {
            final ChannelWrapper cw = this.channelTables.get(address);
            if (cw != null && cw.isOK()) {
                return cw.getChannel();
            }
        }
        final List<String> addrList = this.srvAddrList.get();
        if (this.lockSrvChannel.tryLock(LOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
            try {
                address = this.srvAddrChosen.get();
                if (address != null) {
                    ChannelWrapper cw = this.channelTables.get(address);
                    if (cw != null && cw.isOK()) {
                        return cw.getChannel();
                    }
                }

                if (addrList != null && !addrList.isEmpty()) {
                    for (int i = 0; i < addrList.size(); i++) {
                        int index = this.srvIndex.incrementAndGet();
                        index = Math.abs(index);
                        index = index % addrList.size();
                        String newAddr = addrList.get(index);

                        this.srvAddrChosen.set(newAddr);
                        log.info("new name server is chosen. {}. srvIndex = {}", newAddr, srvIndex);
                        Channel channelNew = this.createChannel(newAddr);
                        if (channelNew != null) {
                            return channelNew;
                        }
                    }
                    throw new NetConnectException(addrList.toString());
                }
            } catch (Exception e) {
                log.error("getAndCreateUseAddressChoosen: create server channel exception", e);
            } finally {
                this.lockSrvChannel.unlock();
            }
        } else {
            log.warn("getAndCreateUseAddressChoosen: try to lock server, but timeout, {}ms", 3000);
        }
        return null;
    }

    @Override
    public void refreshAddressList(List<String> addresses) {
        List<String> old = this.srvAddrList.get();
        boolean update = false;

        if (!addresses.isEmpty()) {
            if (old == null) {
                update = true;
            } else if (addresses.size() != old.size()) {
                update = true;
            } else {
                for (int i = 0; i < addresses.size() && !update; i++) {
                    if (!old.contains(addresses.get(i))) {
                        update = true;
                        break;
                    }
                }
            }

            if (update) {
                Collections.shuffle(addresses);
                log.info("name server address updated. NEW : {} , OLD: {}", addresses, old);
                this.srvAddrList.set(addresses);

                if (!addresses.contains(this.srvAddrChosen.get())) {
                    this.srvAddrChosen.set(null);
                }
            }
        }
    }

    @Override
    public List<String> getAddressList() {
        return this.srvAddrList.get();
    }

    @Override
    public void connect() throws InterruptedException {
        getAndCreateUseAddressChosen();
    }

    @Override
    public void connect(String address) throws InterruptedException {
        getAndCreateChannel(address);
    }

    @Override
    public boolean isChannelWriteable(String address) {
        ChannelWrapper cw = this.channelTables.get(address);
        return cw != null && cw.isOK() && cw.isWriteable();
    }

    @Override
    public boolean isChannelWriteable() {
        ChannelWrapper cw = this.channelTables.get(this.srvAddrChosen.get());
        return cw != null && cw.isOK() && cw.isWriteable();
    }

    @Override
    public ChannelEventListener getChannelEventListener() {
        return this.channelEventListener;
    }

    @Override
    public ExecutorService getCallbackExecutor() {
        return callbackExecutor != null ? callbackExecutor : publicExecutor;
    }


    @Override
    public void setCallbackExecutor(ExecutorService callbackExecutor) {
        this.callbackExecutor = callbackExecutor;
    }

    private Channel createChannel(final String address) throws InterruptedException {
        ChannelWrapper cw = this.channelTables.get(address);
        if (cw != null && cw.isOK()) {
            return cw.getChannel();
        }

        if (this.lockChannelTables.tryLock(LOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
            try {
                boolean createNewConnection;
                cw = this.channelTables.get(address);
                if (cw == null) {
                    createNewConnection = true;
                } else {
                    if (cw.isOK()) {
                        return cw.getChannel();
                    } else if (!cw.getChannelFuture().isDone()) {
                        createNewConnection = false;
                    } else {
                        this.channelTables.remove(address);
                        createNewConnection = true;
                    }
                }

                if (createNewConnection) {
                    ChannelFuture channelFuture = this.bootstrap.connect(NetHelper.string2SocketAddress(address));
                    log.info("createChannel: begin to connect remote {} asynchronously", address);
                    cw = new ChannelWrapper(channelFuture);
                    this.channelTables.put(address, cw);
                }
            } catch (Exception e) {
                log.error("createChannel: create channel exception", e);
            } finally {
                this.lockChannelTables.unlock();
            }
        } else {
            log.warn("createChannel: try to lock channel table, but timeout, {}ms", 30_000);
        }

        if (cw != null) {
            ChannelFuture channelFuture = cw.getChannelFuture();
            if (channelFuture.awaitUninterruptibly(this.nettyClientConfig.getConnectTimeoutMillis())) {
                if (cw.isOK()) {
                    log.info("createChannel: connect remote {} success, {}", address, channelFuture);
                    return cw.getChannel();
                } else {
                    log.warn("createChannel: connect remote {} failed, {}", address, channelFuture,
                            channelFuture.cause());
                }
            } else {
                log.warn("createChannel: connect remote {} timeout {}ms, {}",
                        address, this.nettyClientConfig.getConnectTimeoutMillis(), channelFuture.channel());
            }
        }

        return null;
    }

    private void closeChannel(final Channel channel) {
        if (channel == null) {
            return;
        }
        try {
            if (this.lockChannelTables.tryLock(LOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                try {
                    boolean removeItemFromTable = true;
                    ChannelWrapper wrapper = null;
                    String address = null;
                    for (Map.Entry<String, ChannelWrapper> entry : this.channelTables.entrySet()) {
                        ChannelWrapper cw = entry.getValue();
                        if (cw != null && cw.getChannel() == channel) {
                            address = entry.getKey();
                            wrapper = cw;
                            break;
                        }
                    }

                    if (wrapper == null) {
                        log.info("eventCloseChannel: the channel has been removed from the channel table before");
                        removeItemFromTable = false;
                    }

                    if (removeItemFromTable) {
                        this.channelTables.remove(address);
                        log.info("closeChannel: the channel[{}] was removed from channel table", address);
                        NetHelper.closeChannel(channel);
                    }
                } catch (Exception e) {
                    log.error("closeChannel: close the channel exception", e);
                } finally {
                    this.lockChannelTables.unlock();
                }
            } else {
                log.warn("closeChannel: try to lock channel table, but timeout, {}ms", LOCK_TIMEOUT_MILLIS);
            }
        } catch (InterruptedException e) {
            log.error("closeChannel exception", e);
            Thread.currentThread().interrupt();
        }
    }

    private void closeChannel(final String remote, final Channel channel) {
        if (channel == null) {
            return;
        }
        final String address = remote == null ? NetHelper.remoteAddress(channel) : remote;
        try {
            if (this.lockChannelTables.tryLock(LOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                try {
                    boolean removeItemFromTable = true;
                    final ChannelWrapper wrapper = this.channelTables.get(address);

                    log.info("closeChannel: begin close the channel[{}] Found: {}", remote, wrapper != null);

                    if (wrapper == null) {
                        log.info("closeChannel: the channel has been removed from the channel table before");
                        removeItemFromTable = false;
                    } else if (wrapper.getChannel() != channel) {
                        log.info(
                                "closeChannel: the channel[{}] has been closed before, and has been created again, "
                                        + "nothing to do.",
                                address);
                        removeItemFromTable = false;
                    }

                    if (removeItemFromTable) {
                        this.channelTables.remove(address);
                        log.info("closeChannel: the channel[{}] was removed from channel table", address);
                    }

                    NetHelper.closeChannel(channel);
                } catch (Exception e) {
                    log.error("closeChannel: close the channel exception", e);
                } finally {
                    this.lockChannelTables.unlock();
                }
            } else {
                log.warn("closeChannel: try to lock channel table, but timeout, {}ms", LOCK_TIMEOUT_MILLIS);
            }
        } catch (InterruptedException e) {
            log.error("closeChannel exception", e);
            Thread.currentThread().interrupt();
        }
    }

    private static int initValueIndex() {
        Random r = new Random();
        return Math.abs(r.nextInt() % 999) % 999;
    }

    static class ChannelWrapper {
        private final ChannelFuture channelFuture;

        public ChannelWrapper(ChannelFuture channelFuture) {
            this.channelFuture = channelFuture;
        }

        public boolean isOK() {
            return this.channelFuture.channel() != null && this.channelFuture.channel().isActive() && this.channelFuture
                    .channel().isWritable();
        }

        public boolean isWriteable() {
            return this.channelFuture.channel().isWritable();
        }

        private Channel getChannel() {
            return this.channelFuture.channel();
        }

        public ChannelFuture getChannelFuture() {
            return channelFuture;
        }
    }

    class NettyClientHandler extends SimpleChannelInboundHandler<Message> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Message msg) {
            processMessageReceived(ctx, msg);
        }
    }

    class NettyConnectManageHandler extends ChannelDuplexHandler {
        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            final String remoteAddress = NetHelper.remoteAddress(ctx.channel());
            log.info("NETTY CLIENT PIPELINE: channelInactive {}", remoteAddress);
            closeChannel(ctx.channel());
            super.channelInactive(ctx);

            if (channelEventListener != null) {
                putNettyEvent(new NettyEvent(NettyEventType.CLOSE, remoteAddress, ctx.channel()));
            }
        }

        @Override
        public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress,
                ChannelPromise promise) throws Exception {
            final String local = localAddress == null ? "UNKNOWN" : localAddress.toString();
            final String remote = remoteAddress == null ? "UNKNOWN" : remoteAddress.toString();
            log.info("NETTY CLIENT PIPELINE: CONNECT  {} => {}", local, remote);
            super.connect(ctx, remoteAddress, localAddress, promise);

            if (NettyNetClient.this.channelEventListener != null) {
                NettyNetClient.this.putNettyEvent(new NettyEvent(NettyEventType.CONNECT, remote, ctx.channel()));
            }
        }

        @Override
        public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            final String remoteAddress = NetHelper.remoteAddress(ctx.channel());
            log.info("NETTY CLIENT PIPELINE: DISCONNECT {}", remoteAddress);
            closeChannel(ctx.channel());
            super.disconnect(ctx, promise);

            if (NettyNetClient.this.channelEventListener != null) {
                NettyNetClient.this.putNettyEvent(new NettyEvent(NettyEventType.CLOSE, remoteAddress, ctx.channel()));
            }
        }

        @Override
        public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            final String remoteAddress = NetHelper.remoteAddress(ctx.channel());
            log.info("NETTY CLIENT PIPELINE: CLOSE {}", remoteAddress);
            closeChannel(ctx.channel());
            super.close(ctx, promise);

            NettyNetClient.this.failFast(ctx.channel());
            if (NettyNetClient.this.channelEventListener != null) {
                NettyNetClient.this.putNettyEvent(new NettyEvent(NettyEventType.CLOSE, remoteAddress, ctx.channel()));
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            final String remoteAddress = NetHelper.remoteAddress(ctx.channel());
            log.warn("NETTY CLIENT PIPELINE: exceptionCaught {}", remoteAddress);
            log.warn("NETTY CLIENT PIPELINE: exceptionCaught exception.", cause);
            closeChannel(ctx.channel());

            if (NettyNetClient.this.channelEventListener != null) {
                NettyNetClient.this
                        .putNettyEvent(new NettyEvent(NettyEventType.EXCEPTION, remoteAddress, ctx.channel()));
            }
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof IdleStateEvent) {
                IdleStateEvent event = (IdleStateEvent) evt;
                if (event.state().equals(IdleState.ALL_IDLE)) {
                    final String remoteAddress = NetHelper.remoteAddress(ctx.channel());
                    log.warn("NETTY CLIENT PIPELINE: IDLE exception [{}]", remoteAddress);
                    closeChannel(ctx.channel());

                    if (NettyNetClient.this.channelEventListener != null) {
                        NettyNetClient.this
                                .putNettyEvent(new NettyEvent(NettyEventType.IDLE, remoteAddress, ctx.channel()));
                    }
                }
            }

            ctx.fireUserEventTriggered(evt);
        }
    }
}
