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
import com.alibaba.dubbo.config.ReferenceConfig;
import com.alibaba.dubbo.config.RegistryConfig;
import com.alibaba.dubbo.config.ServiceConfig;

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
    private volatile boolean exported;
    private volatile boolean unexported;
    private volatile ExportHelper exportHelper;
    private volatile ReferHelper referHelper;
    /**
     * The list of ServiceConfig
     */
    private List<ServiceConfig> serviceConfigList;
    private List<ReferenceConfig> referenceConfigList;
    private ApplicationConfig application;
    private List<RegistryConfig> registries;
    private ConsumerConfig consumer;
    private ProviderConfig provider;
    private List<ProtocolConfig> protocols;
    private MonitorConfig monitor;
    private ModuleConfig module;
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

    /**
     * Register service config to bootstrap, which will be called during {@link DubboBootstrap#stop()}
     *
     * @param serviceConfig the service
     * @return the bootstrap instance
     */
    public DubboBootstrap registerServiceConfig(ServiceConfig serviceConfig) {
        serviceConfigList.add(serviceConfig);
        return this;
    }

    public DubboBootstrap registerReferenceConfig(ReferenceConfig referenceConfig) {
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
        if (exportHelper == null) {
            exportHelper = new ExportHelper(this);
        }
        serviceConfigList.forEach(serviceConfig -> {
            exportHelper.setServiceConfig(serviceConfig);
            exportHelper.export();
        });
    }

    public synchronized void export(ServiceConfig serviceConfig) {
        if (exportHelper == null) {
            exportHelper = new ExportHelper(this);
        }
        exportHelper.setServiceConfig(serviceConfig);
        exportHelper.export();
    }

    public synchronized void refer() {
        if (referHelper == null) {
            referHelper = new ReferHelper(this);
        }
        referenceConfigList.forEach(referenceConfig -> {
            referHelper.setReferenceConfig(referenceConfig);
            referHelper.refer();
        });
    }

    public synchronized Object refer(ReferenceConfig referenceConfig) {
        if (referHelper == null) {
            referHelper = new ReferHelper(this);
        }
        referHelper.setReferenceConfig(referenceConfig);
        return referHelper.refer();
    }

    public synchronized void unexport() {
        if (exportHelper == null) {
            return;
        }
        exportHelper.unexport();
    }

    public synchronized void unexport(ServiceConfig serviceConfig) {
        if (exportHelper == null) {
            exportHelper = new ExportHelper(this);
        }
        exportHelper.unexport(serviceConfig);
    }

    public synchronized void unrefer() {
        if (referHelper == null) {
            return;
        }
        referHelper.unrefer();
    }

    public synchronized void unrefer(ReferenceConfig referenceConfig) {
        if (referHelper == null) {
            referHelper = new ReferHelper(this);
        }
        referHelper.unrefer(referenceConfig);
    }

    public List<ServiceConfig> getServiceConfigList() {
        return serviceConfigList;
    }

    public List<ReferenceConfig> getReferenceConfigList() {
        return referenceConfigList;
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

    public ExportHelper getExportHelper() {
        return exportHelper;
    }

    public ReferHelper getReferHelper() {
        return referHelper;
    }
}
