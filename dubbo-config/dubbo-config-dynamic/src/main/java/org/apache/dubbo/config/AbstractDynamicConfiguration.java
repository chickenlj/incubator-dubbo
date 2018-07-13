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
package org.apache.dubbo.config;

import com.alibaba.dubbo.common.config.AbstractConfiguration;
import com.alibaba.dubbo.common.utils.StringUtils;

/**
 *
 */
public abstract class AbstractDynamicConfiguration extends AbstractConfiguration implements DynamicConfiguration {
    private String address;
    private String basePath;

    private String prefix;
    private String env;
    private String serviceKey;
    private String app;

    @Override
    public void addListener(ConfigurationListener listener) {

    }

    @Override
    public Object getProperty(String key, Object defaultValue) {
        Object value;
        if (StringUtils.isNotEmpty(env) && StringUtils.isNotEmpty(prefix)) {
            value = getInternalProperty(prefix + env + "." + key);
        } else {
            value = getInternalProperty(prefix + key);
        }
        if (value == null) {
            value = getInternalProperty(key);
        }
        return value;
    }

    @Override
    public boolean containsKey(String key) {
        return getProperty(key) != null;
    }

    public void setServiceKey(String serviceKey) {
        this.serviceKey = serviceKey;
    }

    public void setEnv(String env) {
        this.env = env;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
}
