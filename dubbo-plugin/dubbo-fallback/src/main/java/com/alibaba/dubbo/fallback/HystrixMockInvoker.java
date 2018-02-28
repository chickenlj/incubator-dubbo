package com.alibaba.dubbo.fallback;

import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcException;

import com.netflix.hystrix.exception.HystrixRuntimeException;

/**
 * @author ken.lj
 * @date 2018/2/27
 */
public class HystrixMockInvoker<T> extends AbstractMockInvoker<T> {

    public HystrixMockInvoker(Invoker<T> invoker) {
        super(invoker);
    }

    @Override
    public Result invoke(Invocation invocation) throws RpcException {
        Result result = null;
        try {
            HystrixCommandExt hystrixCommand = new HystrixCommandExt(invoker, mockInvoker, invocation);
            result = hystrixCommand.execute();
        } catch (Exception e) {
            if (e instanceof HystrixRuntimeException) {
                HystrixRuntimeException hystrixException = (HystrixRuntimeException) e;
                Throwable cause = hystrixException.getCause();
                Throwable fallback = hystrixException.getFallbackException();
                if (fallback != null && fallback instanceof RpcException) {
                    RpcException rpcExe = (RpcException) fallback;
                    //如果是业务方设置的Mock异常，直接抛出cause
                    if (rpcExe.isMock()) {
                        if (cause instanceof RuntimeException) {
                            throw (RuntimeException) cause;
                        } else {//这个分支不应该执行，仅为了容错
                            logger.warn("Expected instance of RuntimeException, got " + cause.getClass().getName(), cause);
                            throw new RuntimeException(cause);
                        }
                    }
                }
                throw new RpcException(hystrixException.getMessage()
                        + (fallback != null ? (" Fallback error msg is " + fallback.getMessage()) : ""), cause, fallback);
            } else {
                //按照hystrix的设计，可能抛出HystrixRuntimeException、HystrtixBadRequestException、IllegalStateException，按照当前dubbo的集成方式，不会抛出后两种异常
                //所以这个if分支应该不会到达
                throw new RpcException(e);
            }
        }
        return result;
    }

    @Override
    protected T getFallback() throws RpcException {
        return null;
    }
}
