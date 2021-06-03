package cn.howardliu.cynomys.net.core.netty;

import java.net.InetSocketAddress;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.howardliu.cynomys.net.core.ChannelEventListener;
import cn.howardliu.cynomys.net.core.InvokeCallback;
import cn.howardliu.cynomys.net.core.NetServer;
import cn.howardliu.cynomys.net.core.common.NetHelper;
import cn.howardliu.cynomys.net.core.common.NetUtil;
import cn.howardliu.cynomys.net.core.common.Pair;
import cn.howardliu.cynomys.net.core.domain.Message;
import cn.howardliu.cynomys.net.core.exception.NetSendRequestException;
import cn.howardliu.cynomys.net.core.exception.NetTimeoutException;
import cn.howardliu.cynomys.net.core.exception.NetTooMuchRequestException;
import cn.howardliu.cynomys.net.core.netty.codec.MessageDecoder;
import cn.howardliu.cynomys.net.core.netty.codec.MessageEncoder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;

/**
 * @author Howard Liu <howardliu1988@163.com>
 * Created on 2021-06-02
 */
public class NettyNetServer extends NettyNetAbstract implements NetServer {
    private static final Logger logger = LoggerFactory.getLogger(NettyNetServer.class);

    private final ServerBootstrap serverBootstrap;
    private final EventLoopGroup eventLoopGroupSelector;
    private final EventLoopGroup eventLoopGroupBoss;
    private final NettyServerConfig nettyServerConfig;

    private final ExecutorService publicExecutor;
    private final ChannelEventListener channelEventListener;

    private final Timer timer = new Timer("scan-response-table-timer", true);

    private DefaultEventExecutorGroup defaultEventExecutorGroup;
    private int port = 0;

    public NettyNetServer(final NettyServerConfig nettyServerConfig) {
        this(nettyServerConfig, null);
    }

    public NettyNetServer(final NettyServerConfig nettyServerConfig, final ChannelEventListener channelEventListener) {
        super(nettyServerConfig.getServerAsyncSemaphoreValue());

        this.nettyServerConfig = nettyServerConfig;
        this.channelEventListener = channelEventListener;
        this.serverBootstrap = new ServerBootstrap();

        int publicThreads = nettyServerConfig.getServerCallbackExecutorThreads();
        if (publicThreads <= 0) {
            publicThreads = 4;
        }

        this.publicExecutor = Executors.newFixedThreadPool(
                publicThreads,
                new ThreadFactory() {
                    private final AtomicInteger threadIndex = new AtomicInteger(0);

                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "netty-server-public-executor-" + this.threadIndex.incrementAndGet());
                    }
                }
        );

        if (useEpoll()) {
            this.eventLoopGroupBoss = new EpollEventLoopGroup(1, new ThreadFactory() {
                private final AtomicInteger threadIndex = new AtomicInteger(0);

                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, "netty-epoll-boss-" + this.threadIndex.incrementAndGet());
                }
            });

            this.eventLoopGroupSelector =
                    new EpollEventLoopGroup(nettyServerConfig.getServerSelectorThreads(), new ThreadFactory() {
                        private final int threadTotal = nettyServerConfig.getServerSelectorThreads();
                        private final AtomicInteger threadIndex = new AtomicInteger(0);

                        @Override
                        public Thread newThread(Runnable r) {
                            return new Thread(r, String.format("netty-server-epoll-selector_%d_%d", threadTotal,
                                    this.threadIndex.incrementAndGet()));
                        }
                    });
        } else {
            this.eventLoopGroupBoss = new NioEventLoopGroup(1, new ThreadFactory() {
                private final AtomicInteger threadIndex = new AtomicInteger(0);

                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, "netty-nio-boss-" + this.threadIndex.incrementAndGet());
                }
            });

            this.eventLoopGroupSelector =
                    new NioEventLoopGroup(nettyServerConfig.getServerSelectorThreads(), new ThreadFactory() {
                        private final int threadTotal = nettyServerConfig.getServerSelectorThreads();
                        private final AtomicInteger threadIndex = new AtomicInteger(0);

                        @Override
                        public Thread newThread(Runnable r) {
                            return new Thread(r, "netty-server-nio-selector-" + threadTotal + "-"
                                    + this.threadIndex.incrementAndGet());
                        }
                    });
        }
    }

    @Override
    public void start() {
        this.defaultEventExecutorGroup = new DefaultEventExecutorGroup(
                nettyServerConfig.getServerWorkerThreads(),
                new ThreadFactory() {
                    private final AtomicInteger threadIndex = new AtomicInteger(0);

                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r,
                                "netty-server-default-event-executor-" + this.threadIndex.incrementAndGet());
                    }
                });


        ServerBootstrap childHandler = this.serverBootstrap
                .group(this.eventLoopGroupBoss, this.eventLoopGroupSelector)
                .channel(useEpoll() ? EpollServerSocketChannel.class : NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 1024)
                .option(ChannelOption.SO_REUSEADDR, true)
                .option(ChannelOption.SO_KEEPALIVE, false)
                .option(ChannelOption.SO_SNDBUF, nettyServerConfig.getServerSocketSndBufSize())
                .option(ChannelOption.SO_RCVBUF, nettyServerConfig.getServerSocketRcvBufSize())
                .childOption(ChannelOption.TCP_NODELAY, true)
                .localAddress(this.nettyServerConfig.getListenPort())
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline()
                                .addLast(new IdleStateHandler(0, 0,
                                        nettyServerConfig.getServerChannelMaxIdleTimeSeconds()))
                                .addLast(new MessageDecoder(nettyServerConfig.getServerSocketMaxFrameLength(), 4, 4))
                                .addLast(new MessageEncoder())
                                .addLast(new NettyConnectManageHandler())
                                .addLast(new NettyServerHandler())
                        ;
                    }
                });

        if (nettyServerConfig.isServerPooledByteBufAllocatorEnable()) {
            childHandler.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
        }

        try {
            final ChannelFuture sync = this.serverBootstrap.bind().sync();
            this.port = ((InetSocketAddress) sync.channel().localAddress()).getPort();
        } catch (InterruptedException e) {
            throw new RuntimeException("this.serverBootstrap.bind().sync() Interrupted", e);
        }

        if (this.channelEventListener != null) {
            this.nettyEventExecutor.start();
        }

        this.timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    NettyNetServer.this.scanResponseTable();
                } catch (Throwable e) {
                    logger.error("scanResponseTable exception", e);
                }
            }
        }, 3L * 1000, 1000);
    }

    @Override
    public void shutdown() {
        try {
            this.timer.cancel();

            this.eventLoopGroupBoss.shutdownGracefully();
            this.eventLoopGroupSelector.shutdownGracefully();

            if (this.nettyEventExecutor != null) {
                this.nettyEventExecutor.shutdown();
            }

            if (this.defaultEventExecutorGroup != null) {
                this.defaultEventExecutorGroup.shutdownGracefully();
            }
        } catch (Exception e) {
            logger.error("NettyNetServer shutdown exception", e);
        }

        if (this.publicExecutor != null) {
            try {
                this.publicExecutor.shutdown();
            } catch (Exception e) {
                logger.error("NettyNetServer shutdown exception, ", e);
            }
        }
    }

    @Override
    public void registerProcessor(byte requestCode, NettyRequestProcessor processor, ExecutorService executor) {
        final ExecutorService executorThis = executor == null ? this.publicExecutor : executor;
        final Pair<NettyRequestProcessor, ExecutorService> pair = new Pair<>(processor, executorThis);
        this.processorTable.put(requestCode, pair);
    }

    @Override
    public Message sync(Channel channel, Message request, long timeoutMillis)
            throws InterruptedException, NetTimeoutException, NetSendRequestException {
        return this.invokeSync(channel, request, timeoutMillis);
    }

    @Override
    public void async(Channel channel, Message request, long timeoutMillis, InvokeCallback invokeCallback)
            throws InterruptedException, NetTimeoutException, NetSendRequestException, NetTooMuchRequestException {
        this.invokeAsync(channel, request, timeoutMillis, invokeCallback);
    }

    @Override
    public void registerDefaultProcessor(NettyRequestProcessor processor, ExecutorService executor) {
        this.defaultRequestProcessor = new Pair<>(processor, executor);
    }

    @Override
    public int localListenPort() {
        return this.port;
    }

    private boolean useEpoll() {
        return NetUtil.isLinuxPlatform()
                && nettyServerConfig.isUseEpollNativeSelector()
                && Epoll.isAvailable();
    }

    @Override
    public ChannelEventListener getChannelEventListener() {
        return channelEventListener;
    }

    @Override
    public ExecutorService getCallbackExecutor() {
        return this.publicExecutor;
    }

    @ChannelHandler.Sharable
    class NettyServerHandler extends SimpleChannelInboundHandler<Message> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Message msg) {
            processMessageReceived(ctx, msg);
        }
    }

    @ChannelHandler.Sharable
    class NettyConnectManageHandler extends ChannelDuplexHandler {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg == null) {
                return;
            }
            final String remoteAddress = NetHelper.remoteAddress(ctx.channel());
            if (logger.isTraceEnabled()) {
                logger.trace("NETTY SERVER PIPELINE: channelRead {}", remoteAddress);
            }
            if (channelEventListener != null && msg instanceof Message) {
                putNettyEvent(new NettyEvent(NettyEventType.READ, remoteAddress, ctx.channel()));
            }
            super.channelRead(ctx, msg);
        }

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
            final String remoteAddress = NetHelper.remoteAddress(ctx.channel());
            logger.info("NETTY SERVER PIPELINE: channelRegistered {}", remoteAddress);
            super.channelRegistered(ctx);
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
            final String remoteAddress = NetHelper.remoteAddress(ctx.channel());
            logger.info("NETTY SERVER PIPELINE: channelUnregistered, the channel[{}]", remoteAddress);
            super.channelUnregistered(ctx);
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            final String remoteAddress = NetHelper.remoteAddress(ctx.channel());
            logger.info("NETTY SERVER PIPELINE: channelActive {}", remoteAddress);
            super.channelActive(ctx);

            if (NettyNetServer.this.channelEventListener != null) {
                NettyNetServer.this.putNettyEvent(new NettyEvent(NettyEventType.CONNECT, remoteAddress, ctx.channel()));
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            final String remoteAddress = NetHelper.remoteAddress(ctx.channel());
            logger.info("NETTY SERVER PIPELINE: channelInactive {}", remoteAddress);
            super.channelInactive(ctx);

            if (NettyNetServer.this.channelEventListener != null) {
                NettyNetServer.this.putNettyEvent(new NettyEvent(NettyEventType.CLOSE, remoteAddress, ctx.channel()));
            }
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof IdleStateEvent) {
                IdleStateEvent event = (IdleStateEvent) evt;
                if (event.state().equals(IdleState.ALL_IDLE)) {
                    final String remoteAddress = NetHelper.remoteAddress(ctx.channel());
                    logger.warn("NETTY SERVER PIPELINE: IDLE exception [{}]", remoteAddress);
                    NetHelper.closeChannel(ctx.channel());

                    if (NettyNetServer.this.channelEventListener != null) {
                        NettyNetServer.this
                                .putNettyEvent(new NettyEvent(NettyEventType.IDLE, remoteAddress, ctx.channel()));
                    }
                }
            }

            ctx.fireUserEventTriggered(evt);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            final String remoteAddress = NetHelper.remoteAddress(ctx.channel());
            logger.info("NETTY SERVER PIPELINE: exceptionCaught {}。", remoteAddress, cause);

            if (NettyNetServer.this.channelEventListener != null) {
                NettyNetServer.this
                        .putNettyEvent(new NettyEvent(NettyEventType.EXCEPTION, remoteAddress, ctx.channel(), cause));
            }

            NetHelper.closeChannel(ctx.channel());
        }
    }
}
