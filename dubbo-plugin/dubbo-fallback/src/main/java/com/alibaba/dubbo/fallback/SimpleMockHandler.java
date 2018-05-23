/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.dubbo.fallback;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.extension.ExtensionLoader;
import com.alibaba.dubbo.common.utils.ConfigUtils;
import com.alibaba.dubbo.common.utils.ReflectUtils;
import com.alibaba.dubbo.common.utils.StringUtils;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.InvokerAdapter;
import com.alibaba.dubbo.rpc.ProxyFactory;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.RpcResult;
import com.alibaba.dubbo.rpc.support.RpcUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author ken.lj
 * @date 22/05/2018
 */
public class SimpleMockHandler implements MockHandler {
    private final static ProxyFactory proxyFactory = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();
    private final static Map<String, Invoker<?>> mocks = new ConcurrentHashMap<String, Invoker<?>>();
    private final static Map<String, Throwable> throwables = new ConcurrentHashMap<String, Throwable>();

    @Override
    public Invoker<?> parseMock(URL url, Invocation invocation) {
        String mock = url.getParameter(invocation.getMethodName() + "." + Constants.MOCK_KEY);

        if (StringUtils.isBlank(mock)) {
            mock = url.getParameter(Constants.MOCK_KEY);
        }

        if (StringUtils.isBlank(mock)) {
            throw new RpcException(new IllegalAccessException("mock can not be null. url :" + url));
        }
        mock = normallizeMock(URL.decode(mock), url.getServiceInterface());
        if (Constants.RETURN_PREFIX.trim().equalsIgnoreCase(mock.trim())) {
            final RpcResult result = new RpcResult();
            result.setValue(null);
            return new InvokerAdapter<Object>() {
                @Override
                public Result invoke(Invocation invocation) throws RpcException {
                    return result;
                }
            };
        } else if (mock.startsWith(Constants.RETURN_PREFIX)) {
            mock = mock.substring(Constants.RETURN_PREFIX.length()).trim();
            mock = mock.replace('`', '"');
            try {
                Type[] returnTypes = RpcUtils.getReturnTypes(invocation);
                final Object value = FallbackUtils.parseMockValue(mock, returnTypes);
                return new InvokerAdapter<Object>() {
                    @Override
                    public Result invoke(Invocation invocation) throws RpcException {
                        return new RpcResult(value);
                    }
                };
            } catch (Exception ew) {
                throw new RpcException("mock return invoke error. method :" + invocation.getMethodName() + ", mock:" + mock + ", url: " + url, ew);
            }
        } else if (mock.startsWith(Constants.THROW_PREFIX)) {
            mock = mock.substring(Constants.THROW_PREFIX.length()).trim();
            mock = mock.replace('`', '"');
            if (StringUtils.isBlank(mock)) {
                throw new RpcException(" mocked exception for Service degradation. ");
            } else { // user customized class
                Throwable t = getThrowable(mock);
                throw new RpcException(RpcException.BIZ_EXCEPTION, t);
            }
        } else { //impl mock
            try {
                return getInvoker(url, mock);
            } catch (Throwable t) {
                throw new RpcException("Failed to create mock implemention class " + mock, t);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> Invoker<T> getInvoker(URL url, String mockService) {
        Invoker<T> invoker = (Invoker<T>) mocks.get(mockService);
        if (invoker != null) {
            return invoker;
        } else {
            Class<T> serviceType = (Class<T>) ReflectUtils.forName(url.getServiceInterface());
            if (ConfigUtils.isDefault(mockService)) {
                mockService = serviceType.getName() + "Mock";
            }

            Class<?> mockClass = ReflectUtils.forName(mockService);
            if (!serviceType.isAssignableFrom(mockClass)) {
                throw new IllegalArgumentException("The mock implemention class " + mockClass.getName() + " not implement interface " + serviceType.getName());
            }

            if (!serviceType.isAssignableFrom(mockClass)) {
                throw new IllegalArgumentException("The mock implemention class " + mockClass.getName() + " not implement interface " + serviceType.getName());
            }
            try {
                T mockObject = (T) mockClass.newInstance();
                invoker = proxyFactory.getInvoker(mockObject, (Class<T>) serviceType, url);
                if (mocks.size() < 10000) {
                    mocks.put(mockService, invoker);
                }
                return invoker;
            } catch (InstantiationException e) {
                throw new IllegalStateException("No such empty constructor \"public " + mockClass.getSimpleName() + "()\" in mock implemention class " + mockClass.getName(), e);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    //mock=fail:throw
    //mock=fail:return
    //mock=xx.Service
    private String normallizeMock(String mock, String interfaceClass) {
        if (mock == null || mock.trim().length() == 0) {
            return mock;
        } else if (ConfigUtils.isDefault(mock) || "fail".equalsIgnoreCase(mock.trim()) || "force".equalsIgnoreCase(mock.trim())) {
            mock = interfaceClass + "Mock";
        }
        if (mock.startsWith(Constants.FAIL_PREFIX)) {
            mock = mock.substring(Constants.FAIL_PREFIX.length()).trim();
        } else if (mock.startsWith(Constants.FORCE_PREFIX)) {
            mock = mock.substring(Constants.FORCE_PREFIX.length()).trim();
        }
        return mock;
    }

    private Throwable getThrowable(String throwstr) {
        Throwable throwable = (Throwable) throwables.get(throwstr);
        if (throwable != null) {
            return throwable;
        } else {
            Throwable t = null;
            try {
                Class<?> bizException = ReflectUtils.forName(throwstr);
                Constructor<?> constructor;
                constructor = ReflectUtils.findConstructor(bizException, String.class);
                t = (Throwable) constructor.newInstance(new Object[]{" mocked exception for Service degradation. "});
                if (throwables.size() < 1000) {
                    throwables.put(throwstr, t);
                }
            } catch (Exception e) {
                throw new RpcException("mock throw error :" + throwstr + " argument error.", e);
            }
            return t;
        }
    }
}
