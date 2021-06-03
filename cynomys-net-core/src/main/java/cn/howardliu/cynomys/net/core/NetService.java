package cn.howardliu.cynomys.net.core;

/**
 * @author Howard Liu <howardliu1988@163.com>
 * Created on 2021-06-01
 */
public interface NetService {
    /**
     * 开启网络链接
     */
    void start();

    /**
     * 关闭网络链接
     */
    void shutdown();
}
