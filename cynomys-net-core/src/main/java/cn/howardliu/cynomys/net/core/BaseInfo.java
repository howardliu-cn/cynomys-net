package cn.howardliu.cynomys.net.core;

import java.util.UUID;
import java.util.concurrent.atomic.LongAdder;

/**
 * 基础信息
 *
 * @author Howard Liu <howardliu1988@163.com>
 * Created on 2021-06-01
 * @see Version
 */
public final class BaseInfo {
    private BaseInfo() {
    }

    /**
     * 当前版本号
     */
    public static final int CURRENT_VERSION = Version.CURRENT_VERSION;
    /**
     * 终端（客户端/服务端）标识
     */
    public static final String CURRENT_TAG = UUID.randomUUID().toString().replaceAll("-", "");
    /**
     * 请求顺序号
     */
    public static final LongAdder REQUEST_SEQ = new LongAdder();
}
