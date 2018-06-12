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
package com.alibaba.dubbo.common.config;

/**
 * TODO load as SPI may be better
 */
public class ConfigurationHolder {

    private static volatile PropertiesConfiguration propertiesConf;
    private static volatile SystemConfiguration systemConf;
    private static volatile EnvironmentConfiguration environmentConf;
    private static volatile CompositeConfiguration compositeConf;

    public static PropertiesConfiguration getPropertiesConf(String prefix, String id) {
        if (propertiesConf != null) {
            return propertiesConf;
        }
        synchronized (ConfigurationHolder.class) {
            if (propertiesConf != null) {
                return propertiesConf;
            }
            propertiesConf = new PropertiesConfiguration(prefix, id);
        }
        return propertiesConf;
    }

    public static SystemConfiguration getSystemConf(String prefix, String id) {
        if (systemConf != null) {
            return systemConf;
        }
        synchronized (ConfigurationHolder.class) {
            if (systemConf != null) {
                return systemConf;
            }
            systemConf = new SystemConfiguration(prefix, id);
        }
        return systemConf;
    }

    public static EnvironmentConfiguration getEnvironmentConf(String prefix, String id) {
        if (environmentConf != null) {
            return environmentConf;
        }
        synchronized (ConfigurationHolder.class) {
            if (environmentConf != null) {
                return environmentConf;
            }
            environmentConf = new EnvironmentConfiguration(prefix, id);
        }
        return environmentConf;
    }

    public static CompositeConfiguration getCompositeConf() {
        return getCompositeConf(null, null);
    }

    public static CompositeConfiguration getCompositeConf(String prefix, String id) {
        if (compositeConf != null) {
            return compositeConf;
        }
        Configuration system = ConfigurationHolder.getPropertiesConf(prefix, id);
        Configuration properties = ConfigurationHolder.getSystemConf(prefix, id);
        return new CompositeConfiguration(system, properties);
    }
}
