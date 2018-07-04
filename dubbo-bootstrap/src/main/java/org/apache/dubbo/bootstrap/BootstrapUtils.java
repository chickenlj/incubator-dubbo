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

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.Version;
import com.alibaba.dubbo.common.config.CompositeConfiguration;
import com.alibaba.dubbo.common.config.Configuration;
import com.alibaba.dubbo.common.config.ConfigurationHolder;
import com.alibaba.dubbo.common.config.InmemoryConfiguration;
import com.alibaba.dubbo.common.extension.ExtensionLoader;
import com.alibaba.dubbo.common.utils.ConfigUtils;
import com.alibaba.dubbo.common.utils.StringUtils;
import com.alibaba.dubbo.common.utils.UrlUtils;
import com.alibaba.dubbo.config.AbstractConfig;
import com.alibaba.dubbo.config.AbstractInterfaceConfig;
import com.alibaba.dubbo.config.ApplicationConfig;
import com.alibaba.dubbo.config.MonitorConfig;
import com.alibaba.dubbo.config.RegistryConfig;
import com.alibaba.dubbo.monitor.MonitorFactory;
import com.alibaba.dubbo.monitor.MonitorService;
import com.alibaba.dubbo.registry.RegistryFactory;
import com.alibaba.dubbo.registry.RegistryService;

import org.apache.dubbo.config.AbstractDynamicConfiguration;
import org.apache.dubbo.config.DynamicConfiguration;
import org.apache.dubbo.config.ServiceConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 */
public class BootstrapUtils {


    private static final String[] SUFFIXES = new String[]{"Config", "Bean"};

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
        Configuration inmemory = toConfiguration(config, defaultPrefix);
        return new CompositeConfiguration(system, inmemory, properties);
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

    public static Map<String, String> configToMapConsideringDynamic(AbstractConfig config, String defaultPrefix, ApplicationConfig application) {
        Map<String, String> map = new HashMap<>();
        if (config == null) {
            return map;
        }
        String prefix = "dubbo." + getTagName(config.getClass()) + ".";
        String id = config.getId();

        Configuration system = ConfigurationHolder.getSystemConf(prefix, id);
        Configuration properties = ConfigurationHolder.getPropertiesConf(prefix, id);
        Configuration inmemory = toConfiguration(config, defaultPrefix);
        AbstractDynamicConfiguration dynamic = (AbstractDynamicConfiguration) ExtensionLoader.getExtensionLoader(DynamicConfiguration.class).getExtension(application.getDynamicType());
        dynamic.setEnv(application.getEnvironment());
        dynamic.setPrefix(prefix);
        CompositeConfiguration compositeConfiguration = new CompositeConfiguration(dynamic, system, inmemory, properties);
        Set<String> keys = config.getMetaData(defaultPrefix).keySet();
        keys.forEach(key -> {
            String value = compositeConfiguration.getString(key);
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

    public static List<URL> loadRegistries(AbstractInterfaceConfig interfaceConfig, boolean provider) {
        List<URL> registryList = new ArrayList<>();
        ApplicationConfig application = interfaceConfig.getApplication();
        List<RegistryConfig> registries = interfaceConfig.getRegistries();
        if (registries != null && !registries.isEmpty()) {
            for (RegistryConfig registryConfig : registries) {
                String address = registryConfig.getAddress();
                if (address == null || address.length() == 0) {
                    address = Constants.ANYHOST_VALUE;
                }
                String sysaddress = System.getProperty("dubbo.registry.address");
                if (sysaddress != null && sysaddress.length() > 0) {
                    address = sysaddress;
                }
                if (address.length() > 0 && !RegistryConfig.NO_AVAILABLE.equalsIgnoreCase(address)) {
                    Map<String, String> map = new HashMap<>();
                    map.putAll(configToMap(application, null));
                    map.putAll(configToMapConsideringDynamic(registryConfig, null, application));
                    map.put("path", RegistryService.class.getName());
                    map.put("dubbo", Version.getProtocolVersion());
                    map.put(Constants.TIMESTAMP_KEY, String.valueOf(System.currentTimeMillis()));
                    if (ConfigUtils.getPid() > 0) {
                        map.put(Constants.PID_KEY, String.valueOf(ConfigUtils.getPid()));
                    }
                    if (!map.containsKey("protocol")) {
                        if (ExtensionLoader.getExtensionLoader(RegistryFactory.class).hasExtension("remote")) {
                            map.put("protocol", "remote");
                        } else {
                            map.put("protocol", "dubbo");
                        }
                    }
                    List<URL> urls = UrlUtils.parseURLs(address, map);
                    for (URL url : urls) {
                        url = url.addParameter(Constants.REGISTRY_KEY, url.getProtocol());
                        url = url.setProtocol(Constants.REGISTRY_PROTOCOL);
                        if ((provider && url.getParameter(Constants.REGISTER_KEY, true))
                                || (!provider && url.getParameter(Constants.SUBSCRIBE_KEY, true))) {
                            registryList.add(url);
                        }
                    }
                }
            }
        }
        return registryList;
    }

    public static URL loadMonitor(AbstractInterfaceConfig interfaceConfig, URL registryURL) {
        MonitorConfig monitor = interfaceConfig.getMonitor();
        if (monitor == null) {
            String monitorAddress = ConfigurationHolder.getCompositeConf().getString("dubbo.monitor.address");
            String monitorProtocol = ConfigurationHolder.getCompositeConf().getString("dubbo.monitor.protocol");
            if ((monitorAddress == null || monitorAddress.length() == 0) && (monitorProtocol == null || monitorProtocol.length() == 0)) {
                return null;
            }

            monitor = new MonitorConfig();
            if (monitorAddress != null && monitorAddress.length() > 0) {
                monitor.setAddress(monitorAddress);
            }
            if (monitorProtocol != null && monitorProtocol.length() > 0) {
                monitor.setProtocol(monitorProtocol);
            }
        }
        Map<String, String> map = new HashMap<>();
        map.put(Constants.INTERFACE_KEY, MonitorService.class.getName());
        map.put("dubbo", Version.getProtocolVersion());
        map.put(Constants.TIMESTAMP_KEY, String.valueOf(System.currentTimeMillis()));
        if (ConfigUtils.getPid() > 0) {
            map.put(Constants.PID_KEY, String.valueOf(ConfigUtils.getPid()));
        }
        map.putAll(configToMapConsideringDynamic(monitor, null, interfaceConfig.getApplication()));
        String address = monitor.getAddress();
        String sysaddress = System.getProperty("dubbo.monitor.address");
        if (sysaddress != null && sysaddress.length() > 0) {
            address = sysaddress;
        }
        if (ConfigUtils.isNotEmpty(address)) {
            if (!map.containsKey(Constants.PROTOCOL_KEY)) {
                if (ExtensionLoader.getExtensionLoader(MonitorFactory.class).hasExtension("logstat")) {
                    map.put(Constants.PROTOCOL_KEY, "logstat");
                } else {
                    map.put(Constants.PROTOCOL_KEY, "dubbo");
                }
            }
            return UrlUtils.parseURL(address, map);
        } else if (Constants.REGISTRY_PROTOCOL.equals(monitor.getProtocol()) && registryURL != null) {
            return registryURL.setProtocol("dubbo").addParameter(Constants.PROTOCOL_KEY, "registry").addParameterAndEncoded(Constants.REFER_KEY, StringUtils.toQueryString(map));
        }
        return null;
    }

}
