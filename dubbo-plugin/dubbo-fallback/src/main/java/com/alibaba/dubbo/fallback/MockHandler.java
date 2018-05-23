package com.alibaba.dubbo.fallback;

/**
 * @author ken.lj
 * @date 2018/2/27
 */

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.extension.Adaptive;
import com.alibaba.dubbo.common.extension.SPI;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;

@SPI("simple")
public interface MockHandler {

    @Adaptive("mocktype")
    Invoker<?> parseMock(URL url, Invocation invocation);
}
