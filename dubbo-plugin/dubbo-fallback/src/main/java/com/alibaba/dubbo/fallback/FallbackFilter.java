package com.alibaba.dubbo.fallback;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.rpc.Filter;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcException;

/**
 * Fallback, Mock, CircuitBreak
 */
public class FallbackFilter implements Filter {

    private FallbackFactory fallbackFactory;

    public void setFallbackFactory(FallbackFactory fallbackFactory) {
        this.fallbackFactory = fallbackFactory;
    }

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        URL url = invoker.getUrl();
        Result result = null;

        if (FallbackUtils.needMock(url)) {// mock=true, 注意mock:force等的处理
//            String value = url.getMethodParameter(invocation.getMethodName(), Constants.MOCK_KEY, Boolean.FALSE.toString()).trim();
            Invoker<?> fallbackInvoker = fallbackFactory.getInvoker(invoker);

            result = fallbackInvoker.invoke(invocation);
        } else { // 如果没提供fallback等配置，直接绕过熔断等
            result = invoker.invoke(invocation);
        }

        return result;
    }
}
