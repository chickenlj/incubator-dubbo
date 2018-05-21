package com.alibaba.dubbo.fallback;

import com.alibaba.dubbo.rpc.Invoker;

/**
 * @author ken.lj
 * @date 2018/2/27
 */
public class HystrixFallbackFactory extends AbstractFallbackFactory {
    @Override
    AbstractFallbackInvoker<?> createInvoker(Invoker<?> invoker) {
        return new HystrixFallbackInvoker(invoker);
    }
}
