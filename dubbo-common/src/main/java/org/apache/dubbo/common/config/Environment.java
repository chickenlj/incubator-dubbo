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
package org.apache.dubbo.common.config;

import org.apache.dubbo.common.utils.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TODO load as SPI may be better
 */
public class Environment {
    private static final String DEFAULT_KEY = "default";
    private static final Environment INSTANCE = new Environment();

    private volatile Map<String, PropertiesConfiguration> propertiesConf = new ConcurrentHashMap<>();
    private volatile Map<String, SystemConfiguration> systemConf = new ConcurrentHashMap<>();
    private volatile Map<String, EnvironmentConfiguration> environmentConf = new ConcurrentHashMap<>();
    private volatile Map<String, CompositeConfiguration> compositeConf = new ConcurrentHashMap<>();

    private AtomicBoolean externalSeted = new AtomicBoolean(false);
    private volatile InmemoryConfiguration externalConfiguration = new InmemoryConfiguration();
    private volatile InmemoryConfiguration appConfiguration = new InmemoryConfiguration();

    public static Environment getInstance() {
        return INSTANCE;
    }

    public PropertiesConfiguration getPropertiesConf(String prefix, String id) {
        return propertiesConf.computeIfAbsent(toKey(prefix, id), k -> new PropertiesConfiguration(prefix, id));
    }

    public SystemConfiguration getSystemConf(String prefix, String id) {
        return systemConf.computeIfAbsent(toKey(prefix, id), k -> new SystemConfiguration(prefix, id));
    }

    public EnvironmentConfiguration getEnvironmentConf(String prefix, String id) {
        return environmentConf.computeIfAbsent(toKey(prefix, id), k -> new EnvironmentConfiguration(prefix, id));
    }

    public void setExternalConfiguration(Map<String, String> properties) {
        if (externalSeted.compareAndSet(false, true)) {
            externalConfiguration.addPropertys(properties);
        }
    }

    public InmemoryConfiguration getAppConfiguration() {
        return appConfiguration;
    }

    public void setAppConfiguration(Map<String, String> properties) {
        this.appConfiguration.addPropertys(properties);
    }

    public CompositeConfiguration getCompositeConf() {
        return getCompositeConf(null, null);
    }

    public CompositeConfiguration getCompositeConf(String prefix, String id) {
        return compositeConf.computeIfAbsent(toKey(prefix, id), k -> {
            CompositeConfiguration compositeConfiguration = new CompositeConfiguration();
            compositeConfiguration.addConfiguration(this.getSystemConf(prefix, id));
            compositeConfiguration.addConfiguration(this.externalConfiguration);
            compositeConfiguration.addConfiguration(appConfiguration);
            compositeConfiguration.addConfiguration(this.getPropertiesConf(prefix, id));
            return compositeConfiguration;
        });
    }

    private static String toKey(String keypart1, String keypart2) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotEmpty(keypart1)) {
            sb.append(keypart1);
        }
        if (StringUtils.isNotEmpty(keypart2)) {
            sb.append(keypart2);
        }

        if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '.') {
            sb.append(".");
        }

        if (sb.length() > 0) {
            return sb.toString();
        }
        return DEFAULT_KEY;
    }

}
