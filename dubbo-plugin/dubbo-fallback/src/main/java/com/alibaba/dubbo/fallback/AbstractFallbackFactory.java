package com.alibaba.dubbo.fallback;

import com.alibaba.dubbo.rpc.Invoker;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author ken.lj
 * @date 2018/2/27
 */
public abstract class AbstractFallbackFactory implements FallbackFactory {

    private ConcurrentMap<String, AbstractFallbackInvoker<?>> fallbackInvokerMap = new ConcurrentHashMap<String, AbstractFallbackInvoker<?>>();

    /**
     * 对于spi的场景，map缓存是不是不应该使用？
     * fullstring用hashcode可以更高效吗
     *
     * @param invoker
     * @return
     */
    @Override
    public Invoker<?> getInvoker(Invoker<?> invoker) {
        String key = invoker.getUrl().toFullString();
        AbstractFallbackInvoker<?> mockInvoker = fallbackInvokerMap.get(key);
        if (mockInvoker == null) {
            fallbackInvokerMap.put(key, createInvoker(invoker));
            mockInvoker = fallbackInvokerMap.get(key);
        }
        return mockInvoker;
    }


    abstract AbstractFallbackInvoker<?> createInvoker(Invoker<?> invoker);
}
