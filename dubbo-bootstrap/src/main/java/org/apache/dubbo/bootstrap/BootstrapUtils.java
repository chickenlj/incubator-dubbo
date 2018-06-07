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
package org.apache.dubbo.bootstrap;

import com.alibaba.dubbo.common.config.CompositeConfiguration;
import com.alibaba.dubbo.common.config.Configuration;
import com.alibaba.dubbo.common.config.ConfigurationHolder;
import com.alibaba.dubbo.common.config.InmemoryConfiguration;
import com.alibaba.dubbo.common.config.PropertiesConfiguration;
import com.alibaba.dubbo.common.config.SystemConfiguration;
import com.alibaba.dubbo.config.AbstractConfig;
import com.alibaba.dubbo.config.ServiceConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 *
 */
public class BootstrapUtils {


    private static final String[] SUFFIXES = new String[]{"Config", "Bean"};

    public static CompositeConfiguration getCompositeConfiguration() {
        Configuration system = new SystemConfiguration();
        Configuration properties = new PropertiesConfiguration();
        return new CompositeConfiguration(system, properties);
    }

    /**
     * @param config
     * @param defaultPrefix "default", only used when {@param config} is ConsumerConfig or ProviderConfig
     * @param prefix        "dubbo.service", "dubbo.provider", ...
     * @param id            {@link ServiceConfig#getId()}
     * @return
     */
    public static CompositeConfiguration getCompositeConfiguration(AbstractConfig config, String defaultPrefix, String prefix, String id) {
        Configuration system = ConfigurationHolder.getSystemConf(prefix, id);
        Configuration properties = ConfigurationHolder.getPropertiesConf(prefix, id);
        return new CompositeConfiguration(system, toConfiguration(config, defaultPrefix), properties);
    }

    /**
     * @param config
     * @param defaultPrefix, "default"
     * @return
     */
    public static Configuration toConfiguration(AbstractConfig config, String defaultPrefix) {
        InmemoryConfiguration configuration = new InmemoryConfiguration();
        if (config == null) {
            return configuration;
        }
        configuration.addPropertys(config.getMetaData(defaultPrefix));
        return configuration;
    }

    public static Map<String, String> configToMap(AbstractConfig config, String defaultPrefix) {
        Map<String, String> map = new HashMap<>();
        if (config == null) {
            return map;
        }
        String prefix = "dubbo." + getTagName(config.getClass()) + ".";
        String id = config.getId();
        Configuration configuration = getCompositeConfiguration(config, defaultPrefix, prefix, id);
        Set<String> keys = config.getMetaData(defaultPrefix).keySet();
        keys.forEach(key -> {
            String value = configuration.getString(key);
            if (value != null) {
                map.put(key, value);
            }
        });

        return map;
    }

    private static String getTagName(Class<?> cls) {
        String tag = cls.getSimpleName();
        for (String suffix : SUFFIXES) {
            if (tag.endsWith(suffix)) {
                tag = tag.substring(0, tag.length() - suffix.length());
                break;
            }
        }
        tag = tag.toLowerCase();
        return tag;
    }

    private static boolean isPrimitive(Class<?> type) {
        return type.isPrimitive()
                || type == String.class
                || type == Character.class
                || type == Boolean.class
                || type == Byte.class
                || type == Short.class
                || type == Integer.class
                || type == Long.class
                || type == Float.class
                || type == Double.class
                || type == Object.class;
    }
}
