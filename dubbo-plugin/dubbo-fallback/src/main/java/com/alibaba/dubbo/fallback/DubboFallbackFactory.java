package com.alibaba.dubbo.fallback;

import com.alibaba.dubbo.rpc.Invoker;

/**
 * @author ken.lj
 * @date 2018/2/27
 */
public class DubboFallbackFactory extends AbstractFallbackFactory {
    @Override
    AbstractMockInvoker<?> createInvoker(Invoker<?> invoker) {
        return null;
    }
}
