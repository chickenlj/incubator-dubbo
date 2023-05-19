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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.alibaba.dubbo.common.Constants.DEFAULT_KEY;

/**
 * Abstract {@link DynamicConfigurationFactory} implementation with cache ability
 *
 * @see DynamicConfigurationFactory
 * @since 2.7.5
 */
public abstract class AbstractDynamicConfigurationFactory implements DynamicConfigurationFactory {

    private volatile ConcurrentMap<String, DynamicConfiguration> dynamicConfigurations = new ConcurrentHashMap<String, DynamicConfiguration>();

    @Override
    public final DynamicConfiguration getDynamicConfiguration(URL url) {
        String key = url == null ? DEFAULT_KEY : url.toServiceString();
        DynamicConfiguration configuration = dynamicConfigurations.get(key);
        if (configuration == null) {
            DynamicConfiguration newConfiguration = createDynamicConfiguration(url);
            dynamicConfigurations.putIfAbsent(key, newConfiguration);
            configuration = dynamicConfigurations.get(key);
        }
        return configuration;
    }

    protected abstract DynamicConfiguration createDynamicConfiguration(URL url);
}
