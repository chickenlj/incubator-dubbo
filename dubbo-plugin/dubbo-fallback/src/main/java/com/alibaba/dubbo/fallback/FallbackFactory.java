package com.alibaba.dubbo.fallback;

import com.alibaba.dubbo.common.extension.Adaptive;
import com.alibaba.dubbo.common.extension.SPI;
import com.alibaba.dubbo.rpc.Invoker;

/**
 * @author ken.lj
 * @date 2018/2/27
 */
@SPI("hystrix")
public interface FallbackFactory {

    @Adaptive("fallback")
    Invoker<?> getInvoker(Invoker<?> invoker);

}
