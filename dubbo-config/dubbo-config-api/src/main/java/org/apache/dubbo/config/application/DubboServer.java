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
package org.apache.dubbo.config.application;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.config.ConfigurationUtils;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ConfigCenterConfig;
import org.apache.dubbo.config.ConsumerConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.config.context.ConfigManager;
import org.apache.dubbo.config.utils.ReferenceConfigCache;
import org.apache.dubbo.registry.Registry;
import org.apache.dubbo.registry.RegistryFactory;
import org.apache.dubbo.rpc.Protocol;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 */
public class DubboServer {

    private static DubboServer DEFAULT_INSTANCE;

    public static synchronized DubboServer getDefault() {
        if (DEFAULT_INSTANCE == null) {
            DEFAULT_INSTANCE = new DubboServer();
        }
        return DEFAULT_INSTANCE;
    }

    private AtomicBoolean status;

    private ConfigManager configManager;

    private List<ServiceConfig> serviceConfigs;
    private List<ReferenceConfig> referenceConfigs;

    private RegistryFactory registryFactory;
    private Protocol protocol;

    public void application(ApplicationConfig applicationConfig) {
        configManager.setApplication(applicationConfig);
    }

    public void configCenter(ConfigCenterConfig configCenterConfig) {
        configManager.setConfigCenter(configCenterConfig);
    }

    public void consumer(ConsumerConfig consumerConfig) {
        configManager.addConsumer(consumerConfig);
    }

    public void protocol(ProtocolConfig protocolConfig) {
        configManager.addProtocol(protocolConfig);
    }

    public void protocols(List<ProtocolConfig> protocolConfigs) {
        configManager.addProtocols(protocolConfigs);
    }

    public void registry(RegistryConfig registryConfig) {
        configManager.addRegistry(registryConfig);
    }

    public void registries(List<RegistryConfig> registryConfigs) {
        configManager.addRegistries(registryConfigs);
    }

    public DubboServer() {
        configManager = new ConfigManager();
    }

    public <T> T refer(ReferenceConfig<T> referenceConfig) {

    }

    public void start() {
        for (ServiceConfig sc : serviceConfigs) {
            // ProtocolConfig 记录endpoint
            // DubboProtocol写入url元数据
            sc.export();
        }

        Optional<List<URL>> registryUrls = loadRegistries(configManager.getRegistries());

        registryUrls.ifPresent(urls -> {
            urls.forEach(registryUrl -> {
                Registry registry = registryFactory.getRegistry(registryUrl);
                registry.register(generateUrlToRegistry());
            });
        });

        if (CollectionUtils.isNotEmpty(referenceConfigs)) {
            registryUrls.ifPresent(urls -> {
                urls.forEach(registryUrl -> {
                    Registry registry = registryFactory.getRegistry(registryUrl);
                    registry.subscribe(generateSubscribeUrl, listener);
                });
            });

            referenceConfigs.forEach(rc -> {
                ReferenceConfigCache.getCache().get(rc);
            });
        }
    }


    public ConfigManager getConfigManager() {
        return configManager;
    }

    private Optional<List<URL>> loadRegistries(List<RegistryConfig> registryConfigs) {

    }

    private URL generateUrlToRegistry() {
        List<ProtocolConfig> protocols = configManager.getProtocols();
        String address = protocols.get(0).getHost();
        Map<String, String> metadata = getInstanceMetadata();
        URL url = new URL();
    }


    private Map<String, String> getInstanceMetadata() {
        Map<String, String> metadata = new HashMap<>();
        String envKeys = ConfigurationUtils.getProperty("dubbo_env_key");
        Arrays.stream(envKeys.split(",")).forEach(key -> {
            metadata.put(key, ConfigurationUtils.getProperty(key));
        });
        return metadata;
    }

    public void stop() {

    }
}
