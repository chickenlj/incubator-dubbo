package com.alibaba.dubbo.fallback;

import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;

/**
 * @author ken.lj
 * @date 2018/2/27
 */
public class DubboFallbackInvoker<T> extends AbstractFallbackInvoker<T> {

    public DubboFallbackInvoker(Invoker<T> invoker) {
        super(invoker);
    }

    @Override
    public Result getFallback() {
        return null;
    }
}
