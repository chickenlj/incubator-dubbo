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

import com.alibaba.dubbo.config.ApplicationConfig;
import com.alibaba.dubbo.config.ConsumerConfig;
import com.alibaba.dubbo.config.ModuleConfig;
import com.alibaba.dubbo.config.MonitorConfig;
import com.alibaba.dubbo.config.ProtocolConfig;
import com.alibaba.dubbo.config.ProviderConfig;
import com.alibaba.dubbo.config.RegistryConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * A bootstrap class to easily start and stop Dubbo via programmatic API.
 * The bootstrap class will be responsible to cleanup the resources during stop.
 */
public class DubboBootstrap {

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

    private ReferenceConfigCache cache;
    /**
     * The shutdown hook used when Dubbo is running under embedded environment
     */
    private DubboShutdownHook shutdownHook;

    public DubboBootstrap() {
        this(true, DubboShutdownHook.getDubboShutdownHook());
    }

    public DubboBootstrap(boolean registerShutdownHookOnStart) {
        this(registerShutdownHookOnStart, DubboShutdownHook.getDubboShutdownHook());
    }

    public DubboBootstrap(boolean registerShutdownHookOnStart, DubboShutdownHook shutdownHook) {
        this.serviceConfigList = new ArrayList<>();
        this.referenceConfigList = new ArrayList<>();
        this.cache = ReferenceConfigCache.getCache();
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
        this.registries.add(registryConfig);
        return this;
    }

    public DubboBootstrap protocolConfig(List<ProtocolConfig> protocolConfigs) {
        this.protocols = protocolConfigs;
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

    /**
     * Register service config to bootstrap, which will be called during {@link DubboBootstrap#stop()}
     *
     * @param serviceConfig the service
     * @return the bootstrap instance
     */
    public DubboBootstrap registerServiceConfig(ServiceConfigBuilder serviceConfig) {
        serviceConfig.setBootstrap(this);
        serviceConfigList.add(serviceConfig);
        return this;
    }

    public DubboBootstrap registerReferenceConfig(ReferenceConfigBuilder referenceConfig) {
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
        referenceConfigList.forEach(referenceConfig -> {
            referenceConfig.setBootstrap(this);
            if (cache.get(referenceConfig) != null) {
                return;
            }
            referenceConfig.refer();
        });
    }

    public synchronized Object refer(ReferenceConfigBuilder referenceConfig) {
        referenceConfig.setBootstrap(this);
        Object ref = cache.get(referenceConfig);
        if (ref != null) {
            return ref;
        }
        return referenceConfig.refer();
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
}
