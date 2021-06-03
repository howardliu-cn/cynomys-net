package cn.howardliu.cynomys.net.core.netty;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import cn.howardliu.cynomys.net.core.InvokeCallback;
import cn.howardliu.cynomys.net.core.NetClient;
import cn.howardliu.cynomys.net.core.NetServer;
import cn.howardliu.cynomys.net.core.SampleChannelEventListener;
import cn.howardliu.cynomys.net.core.domain.Header;
import cn.howardliu.cynomys.net.core.domain.Message;
import cn.howardliu.cynomys.net.core.domain.MessageCode;
import cn.howardliu.cynomys.net.core.domain.MessageType;

/**
 * @author Howard Liu <howardliu1988@163.com>
 * Created on 2021-06-02
 */
public class NettyNetClientServerTest {
    private static NetClient netClient;
    private static NetServer netServer;
    private static final MessageCode TEST_REQ = new MessageCode((byte) 3, "TEST_REQ");
    private static final MessageCode TEST_RESP = new MessageCode((byte) 4, "TEST_RESP");

    @Before
    public void setUp() {
        netServer = new NettyNetServer(new NettyServerConfig(), new SampleChannelEventListener());
        netServer.registerDefaultProcessor((AsyncNettyRequestProcessor) (ctx, request) -> {
            System.out.println("Hi " + ctx.channel().remoteAddress());
            System.out.println("request: " + request);
            final Header header = Header.createHeader();
            header.setCode(TEST_RESP.getCode());
            header.setType(MessageType.RESPONSE.value());
            final Message response = new Message();
            response.setHeader(header);
            response.setBody("this is a response. I got a message: '" + request.getBody() + "'");
            return response;
        }, Executors.newCachedThreadPool());
        netServer.start();

        netClient = new NettyNetClient(new NettyClientConfig());
        netClient.refreshAddressList(Collections.singletonList("127.0.0.1:7911"));
        netClient.start();
    }

    @After
    public void tearDown() {
        netClient.shutdown();
        netServer.shutdown();
    }

    @Test
    public void async() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);

        final Header header = Header.createHeader();
        header.setType(MessageType.REQUEST.value());
        header.setCode(TEST_REQ.getCode());
        final Message message = new Message();
        message.setHeader(header);
        message.setBody("this is a async msg");

        netClient.async(message, 3000, new InvokeCallback() {
            @Override
            protected void operationComplete0(ResponseFuture responseFuture) {
                final Message response = responseFuture.getResponse();
                System.out.println("response: " + response);
                latch.countDown();
            }
        });

        latch.await();
    }

    @Test
    public void sync() throws Exception {
        final Header header = Header.createHeader();
        header.setType(MessageType.REQUEST.value());
        header.setCode(TEST_REQ.getCode());
        final Message message = new Message();
        message.setHeader(header);
        message.setBody("this is a sync msg");
        final Message response = netClient.sync(message, 3000);
        System.out.println(response);
        TimeUnit.MILLISECONDS.sleep(4000);
    }
}
