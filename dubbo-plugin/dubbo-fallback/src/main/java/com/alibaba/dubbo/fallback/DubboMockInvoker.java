package com.alibaba.dubbo.fallback;

import com.alibaba.dubbo.rpc.Invoker;

/**
 * @author ken.lj
 * @date 2018/2/27
 */
public class DubboMockInvoker<T> extends AbstractMockInvoker<T> {

    public DubboMockInvoker(Invoker<T> invoker) {
        super(invoker);
    }

    @Override
    protected T getFallback() {
        return null;
    }
}
