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
package org.apache.dubbo.registry.client;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.config.configcenter.DynamicConfiguration;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.Assert;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.registry.AddressListener;
import org.apache.dubbo.registry.Constants;
import org.apache.dubbo.registry.ProviderFirstParams;
import org.apache.dubbo.registry.integration.AbstractConfiguratorListener;
import org.apache.dubbo.registry.integration.DynamicDirectory;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcServiceContext;
import org.apache.dubbo.rpc.cluster.Configurator;
import org.apache.dubbo.rpc.cluster.RouterChain;
import org.apache.dubbo.rpc.model.ApplicationModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.apache.dubbo.common.constants.RegistryConstants.REGISTRY_TYPE_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.SERVICE_REGISTRY_TYPE;
import static org.apache.dubbo.registry.Constants.CONFIGURATORS_SUFFIX;
import static org.apache.dubbo.rpc.model.ScopeModelUtil.getApplicationModel;

public class ServiceDiscoveryRegistryDirectory<T> extends DynamicDirectory<T> {
    private static final Logger logger = LoggerFactory.getLogger(ServiceDiscoveryRegistryDirectory.class);

    /**
     * instance address to invoker mapping.
     * The initial value is null and the midway may be assigned to null, please use the local variable reference
     */
    private final ConsumerConfigurationListener consumerConfigurationListener;
    private volatile ReferenceConfigurationListener referenceConfigurationListener;
    private volatile boolean enableConfigurationListen = true;
    private volatile List<URL> originalUrls = null;
    private volatile Map<String, String> overrideQueryMap;
    private volatile Map<String, String> consumerFirstQueryMap;
    private final ApplicationModel applicationModel;

    protected volatile Object invokers;


    public ServiceDiscoveryRegistryDirectory(Class<T> fakeType, URL url) {
        super(fakeType, url);
        applicationModel = getApplicationModel(url.getScopeModel());
        consumerConfigurationListener = new ConsumerConfigurationListener(applicationModel);

        Set<ProviderFirstParams> providerFirstParams = ExtensionLoader.getExtensionLoader(ProviderFirstParams.class).getSupportedExtensionInstances();
        if (CollectionUtils.isEmpty(providerFirstParams)) {
            consumerFirstQueryMap = queryMap;
        } else {
            consumerFirstQueryMap = new HashMap<>(queryMap);
            for (ProviderFirstParams paramsFilter : providerFirstParams) {
                if (paramsFilter.params() == null) {
                    break;
                }
                for (String keyToRemove : paramsFilter.params()) {
                    consumerFirstQueryMap.remove(keyToRemove);
                }
            }
        }
    }

    @Override
    public void subscribe(URL url) {
        if (applicationModel.getApplicationEnvironment().getConfiguration().convert(Boolean.class, Constants.ENABLE_CONFIGURATION_LISTEN, true)) {
            enableConfigurationListen = true;
            consumerConfigurationListener.addNotifyListener(this);
            referenceConfigurationListener = new ReferenceConfigurationListener(this.applicationModel, this, url);
        } else {
            enableConfigurationListen = false;
        }
        super.subscribe(url);
    }

    @Override
    public void unSubscribe(URL url) {
        super.unSubscribe(url);
        this.originalUrls = null;
        if (applicationModel.getApplicationEnvironment().getConfiguration().convert(Boolean.class, Constants.ENABLE_CONFIGURATION_LISTEN, true)) {
            consumerConfigurationListener.removeNotifyListener(this);
            referenceConfigurationListener.stop();
        }
    }

    @Override
    public void buildRouterChain(URL url) {
        this.setRouterChain(RouterChain.buildChain(url.addParameter(REGISTRY_TYPE_KEY, SERVICE_REGISTRY_TYPE)));
    }

    @Override
    public boolean isAvailable() {
        if (isDestroyed()) {
            return false;
        }

        List<Invoker<T>> tmpInvokers = (List<Invoker<T>>)invokers;
        if (invokers != null && tmpInvokers.size() > 0) {
            for (Invoker<T> invoker : tmpInvokers) {
                if (invoker.isAvailable()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public synchronized void notify(List<URL> instanceUrls) {
        if (isDestroyed()) {
            return;
        }
        // Set the context of the address notification thread.
        RpcServiceContext.setRpcContext(getConsumerUrl());

        //  3.x added for extend URL address
        ExtensionLoader<AddressListener> addressListenerExtensionLoader = ExtensionLoader.getExtensionLoader(AddressListener.class);
        List<AddressListener> supportedListeners = addressListenerExtensionLoader.getActivateExtension(getUrl(), (String[]) null);
        if (supportedListeners != null && !supportedListeners.isEmpty()) {
            for (AddressListener addressListener : supportedListeners) {
                instanceUrls = addressListener.notify(instanceUrls, getConsumerUrl(), this);
            }
        }
    }

    @Override
    public void notifyInvokers(List<Invoker<?>> invokers) {
        Assert.notNull(invokers, "invokerUrls should not be NULL, use empty url list to clear address.");
        if (invokers.size() == 0) {
            logger.info("Received empty url list...");
            this.forbidden = true;
            this.invokers = Collections.emptyList();
            this.routerChain.setInvokers((List<Invoker<T>>)this.invokers);
            this.destroyAllInvokers();
        } else {
            this.forbidden = false;
            if (CollectionUtils.isEmpty(invokers)) {
                return;
            }
            this.invokers = invokers;
            this.routerChain.setInvokers((List<Invoker<T>>)this.invokers);
        }

        this.invokersChanged();
    }

    // RefreshOverrideAndInvoker will be executed by registryCenter and configCenter, so it should be synchronized.
    private synchronized void refreshOverrideAndInvoker(List<URL> instanceUrls) {
        // mock zookeeper://xxx?mock=return null
        if (enableConfigurationListen) {
            overrideDirectoryUrl();
        }

        // url 如何生效
    }

    // TODO: exact
    private void overrideDirectoryUrl() {
        // merge override parameters
        this.overrideDirectoryUrl = directoryUrl;
        List<Configurator> localAppDynamicConfigurators = consumerConfigurationListener.getConfigurators(); // local reference
        doOverrideUrl(localAppDynamicConfigurators);
        if (referenceConfigurationListener != null) {
            List<Configurator> localDynamicConfigurators = referenceConfigurationListener.getConfigurators(); // local reference
            doOverrideUrl(localDynamicConfigurators);
        }
    }

    private void doOverrideUrl(List<Configurator> configurators) {
        if (CollectionUtils.isNotEmpty(configurators)) {
            for (Configurator configurator : configurators) {
                this.overrideDirectoryUrl = configurator.configure(overrideDirectoryUrl);
                Map<String, String> newParams = new HashMap<>(this.overrideDirectoryUrl.getParameters());
                directoryUrl.getParameters().forEach(newParams::remove);
                this.overrideQueryMap = newParams;
            }
        }
    }

    private InstanceAddressURL overrideWithConfigurator(InstanceAddressURL providerUrl) {
        // override url with configurator from "app-name.configurators"
        providerUrl = overrideWithConfigurators(consumerConfigurationListener.getConfigurators(), providerUrl);

        // override url with configurator from configurators from "service-name.configurators"
        if (referenceConfigurationListener != null) {
            providerUrl = overrideWithConfigurators(referenceConfigurationListener.getConfigurators(), providerUrl);
        }

        return providerUrl;
    }

    private InstanceAddressURL overrideWithConfigurators(List<Configurator> configurators, InstanceAddressURL url) {
        if (CollectionUtils.isNotEmpty(configurators)) {
            // wrap url
            OverrideInstanceAddressURL overrideInstanceAddressURL = new OverrideInstanceAddressURL(url);
            if (overrideQueryMap != null) {
                // override app-level configs
                overrideInstanceAddressURL = (OverrideInstanceAddressURL) overrideInstanceAddressURL.addParameters(overrideQueryMap);
            }
            for (Configurator configurator : configurators) {
                overrideInstanceAddressURL = (OverrideInstanceAddressURL) configurator.configure(overrideInstanceAddressURL);
            }
            return overrideInstanceAddressURL;
        }
        return url;
    }

    @Override
    public boolean isServiceDiscovery() {
        return true;
    }

    @Override
    public boolean isNotificationReceived() {
        return serviceListener == null || serviceListener.isDestroyed()
            || serviceListener.getAllInstances().size() == serviceListener.getServiceNames().size();
    }

    @Override
    public boolean isEmpty() {
        if (invokers == null) {
            return true;
        }
        List<Invoker<T>> invokerList = (List<Invoker<T>>)invokers;
        return CollectionUtils.isEmpty(invokerList);
    }

    /**
     * Close all invokers
     */
    @Override
    protected void destroyAllInvokers() {
        // destroy local invoker list
    }

    private class ReferenceConfigurationListener extends AbstractConfiguratorListener {
        private final ServiceDiscoveryRegistryDirectory<?> directory;
        private final URL url;

        ReferenceConfigurationListener(ApplicationModel applicationModel, ServiceDiscoveryRegistryDirectory<?> directory, URL url) {
            super(applicationModel);
            this.directory = directory;
            this.url = url;
            this.initWith(DynamicConfiguration.getRuleKey(url) + CONFIGURATORS_SUFFIX);
        }

        void stop() {
            this.stopListen(DynamicConfiguration.getRuleKey(url) + CONFIGURATORS_SUFFIX);
        }

        @Override
        protected void notifyOverrides() {
            // to notify configurator/router changes
            if (directory.originalUrls != null) {
                URL backup = RpcContext.getServiceContext().getConsumerUrl();
                RpcContext.getServiceContext().setConsumerUrl(directory.getConsumerUrl());
                directory.refreshOverrideAndInvoker(directory.originalUrls);
                RpcContext.getServiceContext().setConsumerUrl(backup);
            }
        }
    }

    private class ConsumerConfigurationListener extends AbstractConfiguratorListener {
        private final List<ServiceDiscoveryRegistryDirectory<?>> listeners = new ArrayList<>();

        ConsumerConfigurationListener(ApplicationModel applicationModel) {
            super(applicationModel);
        }

        void addNotifyListener(ServiceDiscoveryRegistryDirectory<?> listener) {
            if (listeners.size() == 0) {
                this.initWith(applicationModel.getApplicationName() + CONFIGURATORS_SUFFIX);
            }
            this.listeners.add(listener);
        }

        void removeNotifyListener(ServiceDiscoveryRegistryDirectory<?> listener) {
            this.listeners.remove(listener);
            if (listeners.size() == 0) {
                this.stopListen(applicationModel.getApplicationName() + CONFIGURATORS_SUFFIX);
            }
        }

        @Override
        protected void notifyOverrides() {
            listeners.forEach(listener -> {
                if (listener.originalUrls != null) {
                    URL backup = RpcContext.getServiceContext().getConsumerUrl();
                    RpcContext.getServiceContext().setConsumerUrl(listener.getConsumerUrl());
                    listener.refreshOverrideAndInvoker(listener.originalUrls);
                    RpcContext.getServiceContext().setConsumerUrl(backup);
                }
            });
        }
    }
}
