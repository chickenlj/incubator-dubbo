package com.alibaba.dubbo.fallback;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.RpcResult;
import com.alibaba.dubbo.rpc.service.GenericService;
import com.alibaba.dubbo.rpc.support.RpcUtils;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;

import java.lang.reflect.Method;

/**
 * @author ken.lj
 * @date 2018/2/27
 */
public class HystrixCommandExt extends HystrixCommand<Result> {

    private static final Logger logger = LoggerFactory.getLogger(HystrixCommandExt.class);

    /*
     * static { //
     * HystrixPlugins.getInstance().registerPropertiesStrategy(NocacheHystrixPropertiesStrategy.getInstance()); }
     */

    private Invoker<?> mockInvoker;

    private Invoker<?> invoker;

    private Invocation invocation;

    protected HystrixCommandExt(Invoker<?> invoker, Invoker<?> mockInvoker, Invocation invocation) {
        // groupkey commandkey
        super(Setter
                .withGroupKey(HystrixCommandGroupKey.Factory.asKey(invoker.getInterface().getName()))
                .andCommandKey(HystrixCommandKey.Factory.asKey(getCommandkey(invoker, invocation)))
                .andCommandPropertiesDefaults(
                        HystrixCommandProperties
                                .Setter()
                                .withExecutionIsolationStrategy(HystrixCommandProperties.ExecutionIsolationStrategy.SEMAPHORE)
                                .withExecutionTimeoutEnabled(false)
//                                .withExecutionTimeoutInMilliseconds(500)
                                .withExecutionIsolationSemaphoreMaxConcurrentRequests(
                                        getExecutionIsolationSemaphoreMaxConcurrentRequests(invoker, invocation))
                                .withCircuitBreakerEnabled(getCircuitEnabled(invoker, invocation))
                                .withCircuitBreakerForceOpen(getCircuitBreakForceOpen(invoker, invocation))
                                .withCircuitBreakerForceClosed(getCircuitBreakForceClose(invoker, invocation))
                                .withCircuitBreakerErrorThresholdPercentage(
                                        getCircuitBreakerErrorThresholdPercentage(invoker, invocation))
                                .withCircuitBreakerRequestVolumeThreshold(
                                        getCircuitBreakerRequestVolumeThreshold(invoker, invocation))
                                .withCircuitBreakerSleepWindowInMilliseconds(
                                        getCircuitBreakerSleepWindow(invoker, invocation))
                                .withFallbackIsolationSemaphoreMaxConcurrentRequests(
                                        getFallbackIsolationSemaphoreMaxConcurrentRequests(invoker, invocation))
                                .withRequestCacheEnabled(false)
                                .withFallbackEnabled(getFallbackenabled(invoker, invocation))));

        this.mockInvoker = mockInvoker;
        this.invoker = invoker;
        this.invocation = invocation;
    }

    @Override
    protected Result run() throws Exception {
        Result result = null;
        try {
            result = invoker.invoke(invocation);
        } catch (RpcException e) {
            if (e.isBiz()) {
                return new RpcResult(e);
            } else {
                //在转向容错逻辑前，记录RpcException的error日志
                logger.error("Got RpcException when invoke method " + invocation.getMethodName() + " for the service " + invoker.getUrl().getServiceKey() + ", will go to fallback method.", e);
                throw e;// 统计异常
            }
        } catch (Throwable t) {
            return new RpcResult(t);
        }

        //如果配置要拦截业务异常，则在这里记录业务异常并抛出，这样Hystrix熔断框架会捕获异常并转向容错逻辑；如果配置不拦截业务异常，则返回RpcResult将业务异常原样返回给调用方
        if (checkException(result) && handleBusinessException(invoker, invocation) && getFallbackenabled(invoker, invocation)) {
            logger.error("Got business-related exception when invoke method " + invocation.getMethodName() + " for the service " + invoker.getUrl().getServiceKey() + ", will go to fallback method.", result.getException());
            if (result.getException() instanceof RuntimeException) {
                //理论上在这个位置所有result.getException都会是RuntimeException，只会走到这个分支
                throw (RuntimeException) result.getException();
            } else {
                //目前只是为了容错
                logger.warn("Expected instance of RuntimeException, got " + result.getException().getClass().getName(), result.getException());
                throw new RuntimeException("Caught business exception and mock enabled.", result.getException());
            }
        }

        return result;
    }

    @Override
    protected Result getFallback() {
        Result result = mockInvoker.invoke(invocation);
        //如果业务方的Mock逻辑中返回RpcException.MOCKEXCEPTION，则抛出，这样就能被作为Fallback逻辑异常被处理
        if (result.hasException() && result.getException() instanceof RpcException) {
            RpcException rpcExection = (RpcException) result.getException();
            if (rpcExection.isMock()) {
                throw rpcExection;
            }
        }
        return result;
    }

    private boolean checkException(Result result) {
        if (!result.hasException() || GenericService.class == invoker.getInterface()) {
            return false;
        }

        Throwable exception = result.getException();
        //Checked Exception 不要拦截
        if (!(exception instanceof RuntimeException) && exception instanceof Exception) {
            return false;
        }
        //方法声明的Exception 不要拦截
        try {
            Method method = invoker.getInterface().getMethod(invocation.getMethodName(), invocation.getParameterTypes());
            Class<?>[] exceptionClassses = method.getExceptionTypes();
            for (Class<?> exceptionClass : exceptionClassses) {
                if (exception.getClass().equals(exceptionClass)) {
                    return false;
                }
            }
        } catch (NoSuchMethodException e) {
            return false;
        }

        return true;
    }

    private static String getCommandkey(Invoker<?> invoker, Invocation invocation) {
        return invoker.getUrl().getServiceKey() + "." + RpcUtils.getMethodName(invocation);
    }

    private static boolean getCircuitEnabled(Invoker<?> invoker, Invocation invocation) {
        return invoker.getUrl().getMethodParameter(invocation.getMethodName(), Constants.CIRCUITENABLED_KEY, true);
    }

    private static int getExecutionIsolationSemaphoreMaxConcurrentRequests(Invoker<?> invoker, Invocation invocation) {
        return invoker.getUrl().getMethodParameter(invocation.getMethodName(), Constants.ACTIVES_KEY, 200);
    }

    /*private static String getCommandkey(Invoker<?> invoker, Invocation invocation) {

        StringBuilder method = new StringBuilder(RpcUtils.getMethodName(invocation)).append("("); // 获取方法名

        Class<?>[] parameterTypes = invocation.getParameterTypes();
        for (Class<?> para : parameterTypes) {
            method.append(para.getSimpleName()).append(",");
        }
        // 把最后一个逗号去掉，如果有的话
        if (parameterTypes.length > 0) {
            method.deleteCharAt(method.length() - 1);
        }
        method.append(")");

        return invoker.getInterface().getName() + "." + method.toString();
    }*/

    private static int getCircuitBreakerErrorThresholdPercentage(Invoker<?> invoker, Invocation invocation) {
        return invoker.getUrl().getMethodParameter(invocation.getMethodName(), Constants.ERRORPERCENTAGE_KEY, Constants.DEFAULT_ERRORPERCENTAGE);
    }

    private static int getCircuitBreakerRequestVolumeThreshold(Invoker<?> invoker, Invocation invocation) {
        return invoker.getUrl().getMethodParameter(invocation.getMethodName(), Constants.REQUESTVOLUME_KEY, Constants.DEFAULT_REQUESTVOLUME);
    }

    private static int getCircuitBreakerSleepWindow(Invoker<?> invoker, Invocation invocation) {
        return (int) invoker.getUrl().getMethodParameter(invocation.getMethodName(), Constants.SLEEPWINDOW_KEY, Constants.DEFAULT_SLEEPWINDOW);
    }

    private static int getFallbackIsolationSemaphoreMaxConcurrentRequests(Invoker<?> invoker, Invocation invocation) {
        return invoker.getUrl().getMethodParameter(invocation.getMethodName(), Constants.FALLBACKACTIVES_KEY, Constants.DEFAULT_FALLBACKACTIVES);
    }

    private static boolean getCircuitBreakForceOpen(Invoker<?> invoker, Invocation invocation) {
        boolean forceopen = invoker.getUrl().getMethodParameter(invocation.getMethodName(), Constants.FORCEOPEN_KEY, false);
      /*  if (!forceopen) {
            String mock = invoker.getUrl().getMethodParameter(invocation.getMethodName(),Constants.MOCK_KEY, "");
            if (mock.startsWith("force")) {
                forceopen = true;
            }
        }*/
        return forceopen;
    }

    private static boolean getCircuitBreakForceClose(Invoker<?> invoker, Invocation invocation) {
        boolean forceclose = invoker.getUrl().getMethodParameter(invocation.getMethodName(), Constants.FORCECLOSE_KEY, false);
        /*if (forceclose) {
            String mock = invoker.getUrl().getMethodParameter(invocation.getMethodName(), Constants.MOCK_KEY, "");
            if (mock.startsWith("force")) {
                forceclose = false;
            }
        }*/
        return forceclose;
    }

    private static boolean handleBusinessException(Invoker<?> invoker, Invocation invocation) {
        return invoker.getUrl().getMethodParameter(invocation.getMethodName(), Constants.BIZ_EXCEPTION_KEY, false);
    }

    private static boolean getFallbackenabled(Invoker<?> invoker, Invocation invocation) {
        return invoker.getUrl().getMethodParameter(invocation.getMethodName(), Constants.MOCKENABLED_KEY, true);
    }
}
