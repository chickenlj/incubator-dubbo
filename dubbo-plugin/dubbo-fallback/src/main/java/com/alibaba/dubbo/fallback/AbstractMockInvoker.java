package com.alibaba.dubbo.fallback;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.RpcResult;

public abstract class AbstractMockInvoker<T> implements Invoker<T> {

    private Invoker<T> invoker;

    public AbstractMockInvoker(Invoker<T> invoker) {
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
        Result result = null;
        Throwable cause = null;
        RpcException rpcFallbackException = null;
        if (circuitbreaker.isAllowed(url, invocation)) { // value.startsWith("force")算作强制熔断
            if (logger.isWarnEnabled()) {
                logger.info("force-mock: " + invocation.getMethodName() + " force-mock enabled , url : " + directory.getUrl());
            }
            try {
                //force:direct mock
                result = invoker.invoke(invocation);
                markSuccess();
                return result;
            } catch (RpcException e) {
                if (e.isBiz()) {
                    return new RpcResult(e.getCause());
                }
                markFailure(e); // 根据异常类型来分别统计
                cause = e;
            }
        }

        if (circuitbreaker.isFallbackEnabled(url, invocation)) {
            try {
                result = getFallback();
                markFallbackSuccess();
                return result;
            } catch (Throwable fallbackExe) {
                markFallbackFailure();
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
            throw new RpcException("Circuit opened and fallback not available.", cause, null);
        }
    }

    protected abstract Result getFallback() throws RpcException;

    private void markSuccess() {

    }

    private void markFailure() {

    }

    private void markFallbackSuccess() {

    }

    private void markFallbackFailure() {

    }
}
