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

package com.alibaba.dubbo.configcenter.nacos;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.common.utils.StringUtils;
import com.alibaba.dubbo.configcenter.AbstractDynamicConfiguration;
import com.alibaba.dubbo.configcenter.ConfigChangeType;
import com.alibaba.dubbo.configcenter.ConfigChangedEvent;
import com.alibaba.dubbo.configcenter.ConfigurationListener;
import com.alibaba.dubbo.configcenter.DynamicConfiguration;
import com.alibaba.dubbo.configcenter.MD5Utils;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.AbstractSharedListener;
import com.alibaba.nacos.api.exception.NacosException;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;

import static com.alibaba.dubbo.common.Constants.BACKUP_KEY;
import static com.alibaba.dubbo.common.utils.StringUtils.HYPHEN_CHAR;
import static com.alibaba.dubbo.configcenter.ConfigcenterUtils.getConstantFieldValues;
import static com.alibaba.dubbo.configcenter.ConfigcenterUtils.getDefaultTimeout;
import static com.alibaba.nacos.api.PropertyKeyConst.PASSWORD;
import static com.alibaba.nacos.api.PropertyKeyConst.SERVER_ADDR;
import static com.alibaba.nacos.api.PropertyKeyConst.USERNAME;
import static com.alibaba.nacos.client.constant.Constants.HealthCheck.UP;

/**
 * The nacos implementation of {@link DynamicConfiguration}
 */
public class NacosDynamicConfiguration extends AbstractDynamicConfiguration {

    private static final String GET_CONFIG_KEYS_PATH = "/v1/cs/configs";

    private final Logger logger = LoggerFactory.getLogger(getClass());
    /**
     * the default timeout in millis to get config from nacos
     */
    private static final long DEFAULT_TIMEOUT = 5000L;

    private final Properties nacosProperties;

    private static final String NACOS_RETRY_KEY = "nacos.retry";

    private static final String NACOS_RETRY_WAIT_KEY = "nacos.retry-wait";

    private static final String NACOS_CHECK_KEY = "nacos.check";

    /**
     * The nacos configService
     */
    private final org.apache.dubbo.configcenter.support.nacos.NacosConfigServiceWrapper configService;

    /**
     * The map store the key to {@link NacosConfigListener} mapping
     */
    private final ConcurrentMap<String, NacosConfigListener> watchListenerMap;

    private final MD5Utils md5Utils = new MD5Utils();

    NacosDynamicConfiguration(URL url) {
        super(url);
        this.nacosProperties = buildNacosProperties(url);
        this.configService = buildConfigService(url);
        this.watchListenerMap = new ConcurrentHashMap<String, NacosConfigListener>();
    }

    private org.apache.dubbo.configcenter.support.nacos.NacosConfigServiceWrapper buildConfigService(URL url) {
        int retryTimes = url.getPositiveParameter(NACOS_RETRY_KEY, 10);
        int sleepMsBetweenRetries = url.getPositiveParameter(NACOS_RETRY_WAIT_KEY, 1000);
        boolean check = url.getParameter(NACOS_CHECK_KEY, true);
        ConfigService tmpConfigServices = null;
        try {
            for (int i = 0; i < retryTimes + 1; i++) {
                tmpConfigServices = NacosFactory.createConfigService(nacosProperties);
                if (!check || (UP.equals(tmpConfigServices.getServerStatus()) && testConfigService(tmpConfigServices))) {
                    break;
                } else {
                    logger.warn("Failed to connect to nacos config server. " +
                            (i < retryTimes ? "Dubbo will try to retry in " + sleepMsBetweenRetries + ". " : "Exceed retry max times.") +
                            "Try times: " + (i + 1));
                }
                tmpConfigServices.shutDown();
                tmpConfigServices = null;
                Thread.sleep(sleepMsBetweenRetries);
            }
        } catch (NacosException e) {
            logger.error(e.getErrMsg(), e);
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            logger.error("Interrupted when creating nacos config service client.", e);
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }

        if (tmpConfigServices == null) {
            logger.error("Failed to create nacos config service client. Reason: server status check failed.");
            throw new IllegalStateException("Failed to create nacos config service client. Reason: server status check failed.");
        }

        return new org.apache.dubbo.configcenter.support.nacos.NacosConfigServiceWrapper(tmpConfigServices);
    }

    private boolean testConfigService(ConfigService configService) {
        try {
            configService.getConfig("Dubbo-Nacos-Test", "Dubbo-Nacos-Test", DEFAULT_TIMEOUT);
            return true;
        } catch (NacosException e) {
            return false;
        }
    }

    private Properties buildNacosProperties(URL url) {
        Properties properties = new Properties();
        setServerAddr(url, properties);
        setProperties(url, properties);
        return properties;
    }

    private void setServerAddr(URL url, Properties properties) {
        StringBuilder serverAddrBuilder =
                new StringBuilder(url.getHost()) // Host
                        .append(':')
                        .append(url.getPort()); // Port

        // Append backup parameter as other servers
        String backup = url.getParameter(BACKUP_KEY);
        if (backup != null) {
            serverAddrBuilder.append(',').append(backup);
        }
        String serverAddr = serverAddrBuilder.toString();
        properties.put(SERVER_ADDR, serverAddr);
    }

    private static void setProperties(URL url, Properties properties) {
        // Get the parameters from constants
        Map<String, String> allParameters = url.getParameters();
        Map<String, String> parameters = new HashMap<String, String>();
        for (String prop : getConstantFieldValues(PropertyKeyConst.class)) {
            if (allParameters.get(prop) != null) {
                parameters.put(prop, allParameters.get(prop));
            }
        }
        // Put all parameters
        properties.putAll(parameters);
        if (StringUtils.isNotEmpty(url.getUsername())) {
            properties.put(USERNAME, url.getUsername());
        }
        if (StringUtils.isNotEmpty(url.getPassword())) {
            properties.put(PASSWORD, url.getPassword());
        }
    }


    /**
     * Ignores the group parameter.
     *
     * @param key   property key the native listener will listen on
     * @param group to distinguish different set of properties
     * @return
     */
    private NacosConfigListener createTargetListener(String key, String group) {
        NacosConfigListener configListener = new NacosConfigListener();
        configListener.fillContext(key, group);
        return configListener;
    }

    @Override
    public void addListener(String key, String group, ConfigurationListener listener) {
        String listenerKey = buildListenerKey(key, group);
        NacosConfigListener nacosConfigListener = watchListenerMap.get(listenerKey);
        if (nacosConfigListener == null) {
            NacosConfigListener newListener = createTargetListener(listenerKey, group);
            watchListenerMap.putIfAbsent(listenerKey, newListener);
            nacosConfigListener = watchListenerMap.get(listenerKey);
        }

        nacosConfigListener.addListener(listener);
        try {
            configService.addListener(key, group, nacosConfigListener);
        } catch (NacosException e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public void removeListener(String key, String group, ConfigurationListener listener) {
        String listenerKey = buildListenerKey(key, group);
        NacosConfigListener eventListener = watchListenerMap.get(listenerKey);
        if (eventListener != null) {
            eventListener.removeListener(listener);
        }
    }

    @Override
    public String getConfig(String key, String group, long timeout) throws IllegalStateException {
        try {
            long nacosTimeout = timeout < 0 ? getDefaultTimeout() : timeout;
            if (StringUtils.isEmpty(group)) {
                group = DEFAULT_GROUP;
            }
            return configService.getConfig(key, group, nacosTimeout);
        } catch (NacosException e) {
            logger.error("Failed to get config from nacos with key " + key + ", group " + group + ", error msg is: " + e.getMessage(), e);
        }
        return null;
    }

    public class NacosConfigListener extends AbstractSharedListener {

        private Set<ConfigurationListener> listeners = new CopyOnWriteArraySet<ConfigurationListener>();
        /**
         * cache data to store old value
         */
        private Map<String, String> cacheData = new ConcurrentHashMap<String, String>();

        @Override
        public Executor getExecutor() {
            return null;
        }


        /**
         * receive
         *
         * @param dataId     data ID
         * @param group      group
         * @param configInfo content
         */
        @Override
        public void innerReceive(String dataId, String group, String configInfo) {
            String oldValue = cacheData.get(dataId);
            ConfigChangedEvent event = new ConfigChangedEvent(dataId, group, configInfo, getChangeType(configInfo, oldValue));
            if (configInfo == null) {
                cacheData.remove(dataId);
            } else {
                cacheData.put(dataId, configInfo);
            }
            for (ConfigurationListener listener : listeners) {
                listener.process(event);
            }
        }

        void addListener(ConfigurationListener configurationListener) {

            this.listeners.add(configurationListener);
        }

        void removeListener(ConfigurationListener configurationListener) {
            this.listeners.remove(configurationListener);
        }

        private ConfigChangeType getChangeType(String configInfo, String oldValue) {
            if (StringUtils.isBlank(configInfo)) {
                return ConfigChangeType.DELETED;
            }
            if (StringUtils.isBlank(oldValue)) {
                return ConfigChangeType.ADDED;
            }
            return ConfigChangeType.MODIFIED;
        }
    }

    protected String buildListenerKey(String key, String group) {
        return key + HYPHEN_CHAR + group;
    }
}
