package com.alibaba.dubbo.fallback;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcException;

public abstract class AbstractFallbackInvoker<T> implements FallbackInvoker<T> {

    private Invoker<T> invoker;

    public AbstractFallbackInvoker(Invoker<T> invoker) {
        this.invoker = invoker;
    }

    public Invoker<T> getInvoker() {
        return invoker;
    }

    @Override
    public URL getUrl() {
        return invoker.getUrl();
    }

    @Override
    public boolean isAvailable() {
        return invoker.isAvailable();
    }

    @Override
    public void destroy() {

    }

    @Override
    public Class<T> getInterface() {
        return null;
    }

    @Override
    public Result invoke(Invocation invocation) throws RpcException {
        return null;
    }
}
