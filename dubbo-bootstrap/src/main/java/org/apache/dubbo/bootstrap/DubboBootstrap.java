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

import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ConsumerConfig;
import org.apache.dubbo.config.ModuleConfig;
import org.apache.dubbo.config.MonitorConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ProviderConfig;
import org.apache.dubbo.config.RegistryConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * A bootstrap class to easily start and stop Dubbo via programmatic API.
 * The bootstrap class will be responsible to cleanup the resources during stop.
 */
public class DubboBootstrap {
    private static final DubboBootstrap INSTANCE = new DubboBootstrap(true);

    /**
     * Whether register the shutdown hook during start?
     */
    private volatile boolean registerShutdownHookOnStart;
    /**
     * The list of ServiceConfig
     */
    private List<ServiceConfigBuilder> serviceConfigList;
    private List<ReferenceConfigBuilder> referenceConfigList;

    private ApplicationConfig application;
    private List<RegistryConfig> registries;
    private ConsumerConfig consumer;
    private ProviderConfig provider;
    private List<ProtocolConfig> protocols;
    private MonitorConfig monitor;
    private ModuleConfig module;

    private ReferenceConfigCache cache = ReferenceConfigCache.getCache();
    /**
     * The shutdown hook used when Dubbo is running under embedded environment
     */
    private DubboShutdownHook shutdownHook;

    public static DubboBootstrap getInstance() {
        return INSTANCE;
    }

    public static DubboBootstrap newBootStrap() {
        return new DubboBootstrap(false);
    }

    public static DubboBootstrap newBootStrap(boolean registerShutdownHookOnStart) {
        return new DubboBootstrap(registerShutdownHookOnStart);
    }

    public DubboBootstrap() {
        this(true, DubboShutdownHook.getDubboShutdownHook());
    }

    public DubboBootstrap(boolean registerShutdownHookOnStart) {
        this(registerShutdownHookOnStart, DubboShutdownHook.getDubboShutdownHook());
    }

    public DubboBootstrap(boolean registerShutdownHookOnStart, DubboShutdownHook shutdownHook) {
        this.serviceConfigList = new ArrayList<>();
        this.referenceConfigList = new ArrayList<>();
        this.shutdownHook = shutdownHook;
        this.registerShutdownHookOnStart = registerShutdownHookOnStart;
    }

    public DubboBootstrap applicationConfig(ApplicationConfig applicationConfig) {
        this.application = applicationConfig;
        return this;
    }

    public DubboBootstrap consumerConfig(ConsumerConfig consumerConfig) {
        this.consumer = consumerConfig;
        return this;
    }

    public DubboBootstrap providerConfig(ProviderConfig providerConfig) {
        this.provider = providerConfig;
        return this;
    }

    public DubboBootstrap registryConfigs(List<RegistryConfig> registryConfigs) {
        this.registries = registryConfigs;
        return this;
    }

    public DubboBootstrap registryConfig(RegistryConfig registryConfig) {
        if (this.registries == null) {
            this.registries = new ArrayList<>();
        }
        this.registries.add(registryConfig);
        return this;
    }

    public DubboBootstrap protocolConfigs(List<ProtocolConfig> protocolConfigs) {
        this.protocols = protocolConfigs;
        return this;
    }

    public DubboBootstrap protocolConfig(ProtocolConfig protocolConfig) {
        if (this.protocols == null) {
            this.protocols = new ArrayList<>();
        }
        this.protocols.add(protocolConfig);
        return this;
    }

    public DubboBootstrap moduleConfig(ModuleConfig moduleConfig) {
        this.module = moduleConfig;
        return this;
    }

    public DubboBootstrap monitorConfig(MonitorConfig monitorConfig) {
        this.monitor = monitorConfig;
        return this;
    }

    public DubboBootstrap referenceCache(ReferenceConfigCache cache) {
        this.cache = cache;
        return this;
    }

    public DubboBootstrap cache(ReferenceConfigCache cache) {
        this.cache = cache;
        return this;
    }

    /**
     * Register service config to bootstrap, which will be called during {@link DubboBootstrap#stop()}
     *
     * @param serviceConfig the service
     * @return the bootstrap instance
     */
    public DubboBootstrap serviceConfig(ServiceConfigBuilder serviceConfig) {
        serviceConfig.setBootstrap(this);
        serviceConfigList.add(serviceConfig);
        return this;
    }

    public DubboBootstrap referenceConfig(ReferenceConfigBuilder referenceConfig) {
        referenceConfig.setBootstrap(this);
        referenceConfigList.add(referenceConfig);
        return this;
    }

    public void start() {
        if (registerShutdownHookOnStart) {
            registerShutdownHook();
        } else {
            // DubboShutdown hook has been registered in AbstractConfig,
            // we need to remove it explicitly
            removeShutdownHook();
        }
        export();
        refer();
    }

    public void stop() {
        unexport();
        unrefer();
        shutdownHook.destroyAll();
        if (registerShutdownHookOnStart) {
            removeShutdownHook();
        }
    }

    /**
     * Register the shutdown hook
     */
    public DubboBootstrap registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        return this;
    }

    /**
     * Remove this shutdown hook
     */
    public DubboBootstrap removeShutdownHook() {
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ex) {
            // ignore - VM is already shutting down
        }
        return this;
    }

    public synchronized void export() {
        serviceConfigList.forEach(serviceConfig -> {
            serviceConfig.setBootstrap(this);
            serviceConfig.export();
        });
    }

    public synchronized void export(ServiceConfigBuilder serviceConfig) {
        serviceConfig.setBootstrap(this);
        serviceConfig.export();
    }

    public synchronized void refer() {
        refer(true);
    }

    public synchronized Object refer(ReferenceConfigBuilder referenceConfig) {
        return refer(referenceConfig, true);
    }

    public synchronized Object refer(ReferenceConfigBuilder referenceConfig, boolean isCache) {
        referenceConfig.setBootstrap(this);
        if (isCache) {
            return cache.get(referenceConfig);
        }
        return referenceConfig.refer();
    }

    public synchronized void refer(boolean isCache) {
        referenceConfigList.forEach(referenceConfig -> {
            referenceConfig.setBootstrap(this);
            if (isCache) {
                cache.get(referenceConfig);
            } else {
                referenceConfig.refer();
            }
        });
    }

    public synchronized void unexport() {
        serviceConfigList.forEach(ServiceConfigBuilder::unexport);
    }

    public synchronized void unexport(ServiceConfigBuilder serviceConfigBuilder) {
        serviceConfigBuilder.unexport();
    }

    public synchronized void unrefer() {
        cache.destroyAll();
    }

    public synchronized void unrefer(ReferenceConfigBuilder referenceConfig) {
        cache.destroy(referenceConfig);
    }

    public ApplicationConfig getApplication() {
        return application;
    }

    public List<RegistryConfig> getRegistries() {
        return registries;
    }

    public ConsumerConfig getConsumer() {
        return consumer;
    }

    public ProviderConfig getProvider() {
        return provider;
    }

    public List<ProtocolConfig> getProtocols() {
        return protocols;
    }

    public MonitorConfig getMonitor() {
        return monitor;
    }

    public ModuleConfig getModule() {
        return module;
    }

    public void setRegisterShutdownHookOnStart(boolean register) {
        this.registerShutdownHookOnStart = register;
    }

    public ReferenceConfigCache getCache() {
        return cache;
    }
}
