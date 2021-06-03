package cn.howardliu.cynomys.net.core;

import cn.howardliu.cynomys.net.core.domain.Message;

/**
 * @author Howard Liu <howardliu1988@163.com>
 * Created on 2021-06-02
 */
public interface NetResponseCallback {
    void callback(Message response);
}
