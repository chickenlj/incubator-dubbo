package com.alibaba.dubbo.fallback;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.metrics.RpcMetrics;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.RpcResult;

/**
 * @author ken.lj
 * @date 2018/2/27
 */
public class DubboFallbackInvoker<T> extends AbstractFallbackInvoker<T> {

    private RpcMetrics metrics;

    public DubboFallbackInvoker(Invoker<T> invoker) {
        super(invoker);
    }

    @Override
    public Result invoke(Invocation invocation) throws RpcException {
        Result result = null;
        Throwable cause = null;
        RpcException rpcFallbackException = null;

        if (!circuitbreaker.isAllowed(url, invocation)) { // value.startsWith("force")算作强制熔断
            logger.error();
            metrics.markShortCircuited();
            return getFallback();
        }

        try {
            metrics.incrConcurrent();
            //force:direct mock
            result = invoker.invoke(invocation);
            metrics.markSuccess();
            return result;
        } catch (RpcException e) {
            if (e.isBiz()) {
                return new RpcResult(e.getCause());
            }
            metrics.markFailure(e); // 根据异常类型来分别统计
            cause = e;
        } catch (Throwable unexpectdExe) {
            metrics.markFailure(unexpectdExe); // 根据异常类型来分别统计
            cause = unexpectdExe;
        }

        if (circuitbreaker.isFallbackEnabled(url, invocation)) {
            try {
                metrics.incrFallbackConcurrent();
                result = getFallback();
                metrics.markFallbackSuccess();
                return result;
            } catch (Throwable fallbackExe) {
                metrics.markFallbackFailure();
                if (cause == null) {
                    rpcFallbackException = new RpcException("Circuit opened and fallback failed. ", null, fallbackExe);
                } else {
                    rpcFallbackException = new RpcException("Remote call failed and fallback failed. ", cause, fallbackExe);
                }
            }
        }

        if (rpcFallbackException != null) {
            throw rpcFallbackException;
        } else {
            if (cause instanceof RpcException) {
                throw (RpcException) cause;
            }
            throw new RpcException(cause);
        }
    }

    public Result getFallbackOrThrowException(URL url, Invocation invocation, Exception cause) throws RpcException {
        if (circuitbreaker.isFallbackEnabled(url, invocation)) {
            try {
                metrics.incrFallbackConcurrent();
                result = getFallback();
                metrics.markFallbackSuccess();
                return result;
            } catch (Throwable fallbackExe) {
                metrics.markFallbackFailure();
                if (cause == null) {
                    throw new RpcException("Circuit opened and fallback failed. ", null, fallbackExe);
                } else {
                    throw new RpcException("Remote call failed and fallback failed. ", cause, fallbackExe);
                }
            }
        } else {
            throw new RpcException("Circuit opened and fallback disabled. ", cause, null);
        }
    }

    @Override
    public Result getFallback() {
        return null;
    }
}
