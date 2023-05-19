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
package com.alibaba.dubbo.configcenter;


import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.alibaba.dubbo.common.Constants.GROUP_KEY;
import static com.alibaba.dubbo.common.Constants.TIMEOUT_KEY;
import static com.alibaba.dubbo.configcenter.ConfigcenterUtils.getDefaultTimeout;


/**
 * The abstract implementation of {@link DynamicConfiguration}
 *
 * @since 2.7.5
 */
public abstract class AbstractDynamicConfiguration implements DynamicConfiguration {

    public static final String PARAM_NAME_PREFIX = "dubbo.config-center.";

    public static final String THREAD_POOL_PREFIX_PARAM_NAME = PARAM_NAME_PREFIX + "thread-pool.prefix";

    public static final String DEFAULT_THREAD_POOL_PREFIX = PARAM_NAME_PREFIX + "workers";

    public static final String THREAD_POOL_SIZE_PARAM_NAME = PARAM_NAME_PREFIX + "thread-pool.size";

    /**
     * The keep alive time in milliseconds for threads in {@link ThreadPoolExecutor}
     */
    public static final String THREAD_POOL_KEEP_ALIVE_TIME_PARAM_NAME = PARAM_NAME_PREFIX + "thread-pool.keep-alive-time";

    /**
     * The parameter name of group for config-center
     *
     * @since 2.7.8
     */
    public static final String GROUP_PARAM_NAME = PARAM_NAME_PREFIX + GROUP_KEY;

    /**
     * The parameter name of timeout for config-center
     *
     * @since 2.7.8
     */
    public static final String TIMEOUT_PARAM_NAME = PARAM_NAME_PREFIX + TIMEOUT_KEY;

    public static final int DEFAULT_THREAD_POOL_SIZE = 1;

    /**
     * Default keep alive time in milliseconds for threads in {@link ThreadPoolExecutor} is 1 minute( 60 * 1000 ms)
     */
    public static final long DEFAULT_THREAD_POOL_KEEP_ALIVE_TIME = TimeUnit.MINUTES.toMillis(1);

    /**
     * Logger
     */
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected AbstractDynamicConfiguration(URL url) {

    }

    @Override
    public void addListener(String key, ConfigurationListener listener) {
        addListener(key, ConfigcenterUtils.getDefaultGroup(), listener);
    }


    /**
     * {@link #removeListener(String, String, ConfigurationListener)}
     *
     * @param key      the key to represent a configuration
     * @param listener configuration listener
     */
    @Override
    public void removeListener(String key, ConfigurationListener listener) {
        removeListener(key, ConfigcenterUtils.getDefaultGroup(), listener);
    }

    public String getConfig(String key, String group) {
        return getConfig(key, group, getDefaultTimeout());
    }

}
