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
package org.apache.dubbo.registry.client.event.listener;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.threadpool.manager.ExecutorRepository;
import org.apache.dubbo.common.utils.Assert;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.metadata.MetadataInfo;
import org.apache.dubbo.metadata.MetadataService;
import org.apache.dubbo.registry.NotifyListener;
import org.apache.dubbo.registry.client.DefaultServiceInstance;
import org.apache.dubbo.registry.client.InstanceAddressURL;
import org.apache.dubbo.registry.client.RegistryClusterIdentifier;
import org.apache.dubbo.registry.client.ServiceDiscovery;
import org.apache.dubbo.registry.client.ServiceInstance;
import org.apache.dubbo.registry.client.event.RetryServiceInstancesChangedEvent;
import org.apache.dubbo.registry.client.event.ServiceInstancesChangedEvent;
import org.apache.dubbo.registry.client.metadata.MetadataUtils;
import org.apache.dubbo.registry.client.metadata.ServiceInstanceMetadataUtils;
import org.apache.dubbo.registry.client.metadata.store.RemoteMetadataServiceImpl;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Protocol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.apache.dubbo.common.constants.CommonConstants.DISABLED_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.ENABLED_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.PROTOCOL_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.REMOTE_METADATA_STORAGE_TYPE;
import static org.apache.dubbo.common.constants.RegistryConstants.EMPTY_PROTOCOL;
import static org.apache.dubbo.metadata.RevisionResolver.EMPTY_REVISION;
import static org.apache.dubbo.registry.client.metadata.ServiceInstanceMetadataUtils.getExportedServicesRevision;

/**
 * The Service Discovery Changed Listener
 *
 * @see ServiceInstancesChangedEvent
 * @since 2.7.5
 */
public class ServiceInstancesChangedListener {

    private static final Logger logger = LoggerFactory.getLogger(ServiceInstancesChangedListener.class);

    protected final Set<String> serviceNames;
    protected final ServiceDiscovery serviceDiscovery;
    protected URL url;
    protected Map<String, NotifyListener> listeners;
    protected AtomicBoolean destroyed = new AtomicBoolean(false);

    protected Map<String, List<ServiceInstance>> allInstances;
    protected Map<String, MetadataInfo> revisionToMetadata;

    private volatile long lastRefreshTime;
    private Semaphore retryPermission;
    private volatile ScheduledFuture<?> retryFuture;
    private static ScheduledExecutorService scheduler = ExtensionLoader.getExtensionLoader(ExecutorRepository.class).getDefaultExtension().getMetadataRetryExecutor();

    private Map<String, Invoker<?>> addressToInvoker;
    private Protocol protocol;

    public ServiceInstancesChangedListener(Set<String> serviceNames, ServiceDiscovery serviceDiscovery, Protocol protocol) {
        this.serviceNames = serviceNames;
        this.serviceDiscovery = serviceDiscovery;
        this.listeners = new ConcurrentHashMap<>();
        this.allInstances = new HashMap<>();
        this.revisionToMetadata = new HashMap<>();
        retryPermission = new Semaphore(1);
        this.protocol = protocol;
    }

    /**
     * On {@link ServiceInstancesChangedEvent the service instances change event}
     *
     * @param event {@link ServiceInstancesChangedEvent}
     */
    public synchronized void onEvent(ServiceInstancesChangedEvent event) {
        if (destroyed.get() || !accept(event) || isRetryAndExpired(event)) {
            return;
        }

        refreshInstance(event);

        if (logger.isDebugEnabled()) {
            logger.debug(event.getServiceInstances().toString());
        }

        Map<String, List<ServiceInstance>> revisionToInstances = new HashMap<>();
        Map<String, MetadataInfo> newRevisionToMetadata = new HashMap<>();

        // grouping all instances of this app(service name) by revision
        for (Map.Entry<String, List<ServiceInstance>> entry : allInstances.entrySet()) {
            List<ServiceInstance> instances = entry.getValue();
            for (ServiceInstance instance : instances) {
                String revision = getExportedServicesRevision(instance);
                if (revision == null || EMPTY_REVISION.equals(revision)) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Find instance without valid service metadata: " + instance.getAddress());
                    }
                    continue;
                }
                List<ServiceInstance> subInstances = revisionToInstances.computeIfAbsent(revision, r -> new LinkedList<>());
                subInstances.add(instance);
            }
        }

        // get MetadataInfo with revision
        for (Map.Entry<String, List<ServiceInstance>> entry : revisionToInstances.entrySet()) {
            String revision = entry.getKey();
            List<ServiceInstance> subInstances = entry.getValue();
            ServiceInstance instance = selectInstance(subInstances);
            MetadataInfo metadata = getRemoteMetadata(revision, instance);
            // update metadata into each instance, in case new instance created.
            for (ServiceInstance tmpInstance : subInstances) {
                ((DefaultServiceInstance) tmpInstance).setServiceMetadata(metadata);
            }
//            ((DefaultServiceInstance) instance).setServiceMetadata(metadata);
            newRevisionToMetadata.putIfAbsent(revision, metadata);
        }

        if (logger.isDebugEnabled()) {
            logger.debug(newRevisionToMetadata.size() + " unique revisions: " + newRevisionToMetadata.keySet());
        }

        if (hasEmptyMetadata(newRevisionToMetadata)) {// retry every 10 seconds
            if (retryPermission.tryAcquire()) {
                retryFuture = scheduler.schedule(new AddressRefreshRetryTask(retryPermission, event.getServiceName()), 10_000L, TimeUnit.MILLISECONDS);
                logger.warn("Address refresh try task submitted.");
            }
            logger.error("Address refresh failed because of Metadata Server failure, wait for retry or new address refresh event.");
            return;
        }

        this.revisionToMetadata = newRevisionToMetadata;

        List<URL> instanceAddressURLs = toUrls(revisionToInstances);
        refreshInvoker(instanceAddressURLs);
        this.notifyAddressChanged(instanceAddressURLs);
    }

    private List<URL> toUrls(Map<String, List<ServiceInstance>> revisionToInstances) {
        List<URL> urls = new ArrayList<>();
        for (Map.Entry<String, List<ServiceInstance>> entry : revisionToInstances.entrySet()) {
            for (ServiceInstance instance : entry.getValue()) {
                // different protocols may have ports specified in meta
                if (ServiceInstanceMetadataUtils.hasEndpoints(instance)) {
                    List<DefaultServiceInstance.Endpoint> endpoints = ((DefaultServiceInstance) instance).getEndpoints();
                    for (DefaultServiceInstance.Endpoint endpoint : endpoints) {
                        if (endpoint != null) {
                            if (!endpoint.getPort().equals(instance.getPort())) {
                                urls.add(((DefaultServiceInstance) instance).copyFrom(endpoint).toURL());
                            } else {
                                instance.getMetadata().put(PROTOCOL_KEY, endpoint.getProtocol());
                                urls.add(instance.toURL());
                            }
                        }
                    }
                }

                // FIXME
                instance.getExtendParams().putAll(url.getParameters());
            }
        }
        return urls;
    }

    public synchronized void addListenerAndNotify(String serviceKey, NotifyListener listener) {
        this.listeners.put(serviceKey, listener);
        if (CollectionUtils.isNotEmptyMap(addressToInvoker)) {
            List<Invoker<?>> invokers = Collections.unmodifiableList(new ArrayList<>(addressToInvoker.values()));
            listener.notifyInvokers(invokers);
        }
    }

    public void removeListener(String serviceKey) {
        listeners.remove(serviceKey);
        logger.info("Interface listener of interface " + serviceKey + " removed.");
        if (listeners.isEmpty()) {
            logger.info("No interface listeners exist, will stop instance listener for " + this.getServiceNames());
            serviceDiscovery.removeServiceInstancesChangedListener(this);
            this.destroy();
        }
    }

    public boolean hasListeners() {
        return CollectionUtils.isNotEmptyMap(listeners);
    }

    /**
     * Get the correlative service name
     *
     * @return the correlative service name
     */
    public final Set<String> getServiceNames() {
        return serviceNames;
    }

    public void setUrl(URL url) {
        this.url = url;
    }

    public URL getUrl() {
        return url;
    }

    public Map<String, List<ServiceInstance>> getAllInstances() {
        return allInstances;
    }

    public List<ServiceInstance> getInstancesOfApp(String appName) {
        return allInstances.get(appName);
    }

    public Map<String, MetadataInfo> getRevisionToMetadata() {
        return revisionToMetadata;
    }

    public MetadataInfo getMetadata(String revision) {
        return revisionToMetadata.get(revision);
    }

    /**
     * @param event {@link ServiceInstancesChangedEvent event}
     * @return If service name matches, return <code>true</code>, or <code>false</code>
     */
    private boolean accept(ServiceInstancesChangedEvent event) {
        return serviceNames.contains(event.getServiceName());
    }

    protected boolean isRetryAndExpired(ServiceInstancesChangedEvent event) {
        if (event instanceof RetryServiceInstancesChangedEvent) {
            RetryServiceInstancesChangedEvent retryEvent = (RetryServiceInstancesChangedEvent) event;
            logger.warn("Received address refresh retry event, " + retryEvent.getFailureRecordTime());
            if (retryEvent.getFailureRecordTime() < lastRefreshTime && !hasEmptyMetadata(revisionToMetadata)) {
                logger.warn("Ignore retry event, event time: " + retryEvent.getFailureRecordTime() + ", last refresh time: " + lastRefreshTime);
                return true;
            }
            logger.warn("Retrying address notification...");
        }
        return false;
    }

    private void refreshInstance(ServiceInstancesChangedEvent event) {
        if (event instanceof RetryServiceInstancesChangedEvent) {
            return;
        }
        String appName = event.getServiceName();
        List<ServiceInstance> appInstances = event.getServiceInstances();
        logger.info("Received instance notification, serviceName: " + appName + ", instances: " + appInstances.size());
        allInstances.put(appName, appInstances);
        lastRefreshTime = System.currentTimeMillis();
    }

    protected boolean hasEmptyMetadata(Map<String, MetadataInfo> revisionToMetadata) {
        if (revisionToMetadata == null) {
            return false;
        }
        for (Map.Entry<String, MetadataInfo> entry : revisionToMetadata.entrySet()) {
            if (entry.getValue() == MetadataInfo.EMPTY) {
                return true;
            }
        }
        return false;
    }

    protected MetadataInfo getRemoteMetadata(String revision, ServiceInstance instance) {
        MetadataInfo metadata = revisionToMetadata.get(revision);

        if (metadata != null && metadata != MetadataInfo.EMPTY) {
            // metadata loaded from cache
            if (logger.isDebugEnabled()) {
                logger.debug("MetadataInfo for instance " + instance.getAddress() + "?revision=" + revision + "&cluster=" + instance.getRegistryCluster() + ", " + metadata);
            }
            return metadata;
        }

        // try to load metadata from remote.
        int triedTimes = 0;
        while (triedTimes < 3) {
            metadata = doGetMetadataInfo(instance);

            if (metadata != MetadataInfo.EMPTY) {// succeeded
                break;
            } else {// failed
                logger.error("Failed to get MetadataInfo for instance " + instance.getAddress() + "?revision=" + revision
                        + "&cluster=" + instance.getRegistryCluster() + ", wait for retry.");
                triedTimes++;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                }
            }
        }

        revisionToMetadata.putIfAbsent(revision, metadata);
        return metadata;
    }

    protected MetadataInfo doGetMetadataInfo(ServiceInstance instance) {
        String metadataType = ServiceInstanceMetadataUtils.getMetadataStorageType(instance);
        // FIXME, check "REGISTRY_CLUSTER_KEY" must be set by every registry implementation.
        if (instance.getRegistryCluster() == null) {
            instance.setRegistryCluster(RegistryClusterIdentifier.getExtension(url).consumerKey(url));
        }
        MetadataInfo metadataInfo;
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Instance " + instance.getAddress() + " is using metadata type " + metadataType);
            }
            if (REMOTE_METADATA_STORAGE_TYPE.equals(metadataType)) {
                RemoteMetadataServiceImpl remoteMetadataService = MetadataUtils.getRemoteMetadataService(instance.getApplicationModel());
                metadataInfo = remoteMetadataService.getMetadata(instance);
            } else {
                // change the instance used to communicate to avoid all requests route to the same instance
                MetadataService metadataServiceProxy = MetadataUtils.getMetadataServiceProxy(instance);
                metadataInfo = metadataServiceProxy.getMetadataInfo(ServiceInstanceMetadataUtils.getExportedServicesRevision(instance));
                MetadataUtils.destroyMetadataServiceProxy(instance);
            }
        } catch (Exception e) {
            logger.error("Failed to load service metadata, meta type is " + metadataType, e);
            metadataInfo = null;
        }

        if (metadataInfo == null) {
            metadataInfo = MetadataInfo.EMPTY;
        }
        return metadataInfo;
    }

    private ServiceInstance selectInstance(List<ServiceInstance> instances) {
        if (instances.size() == 1) {
            return instances.get(0);
        }
        return instances.get(ThreadLocalRandom.current().nextInt(0, instances.size()));
    }

    protected void notifyAddressChanged(List<URL> instanceAddressURLs) {

        List<Invoker<?>> invokers = Collections.unmodifiableList(new ArrayList<>(addressToInvoker.values()));
        listeners.forEach((key, notifyListener) -> {
            notifyListener.notify(instanceAddressURLs);
            notifyListener.notifyInvokers(invokers);
        });
    }

    /**
     * Since this listener is shared among interfaces, destroy this listener only when all interface listener are unsubscribed
     */
    public synchronized void destroy() {
        if (!destroyed.get()) {
            if (CollectionUtils.isEmptyMap(listeners)) {
                if (destroyed.compareAndSet(false, true)) {
                    allInstances.clear();
                    revisionToMetadata.clear();
                    if (retryFuture != null && !retryFuture.isDone()) {
                        retryFuture.cancel(true);
                    }
                }
            }
        }
    }

    public boolean isDestroyed() {
        return destroyed.get();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ServiceInstancesChangedListener)) {
            return false;
        }
        ServiceInstancesChangedListener that = (ServiceInstancesChangedListener) o;
        return Objects.equals(getServiceNames(), that.getServiceNames());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass(), getServiceNames());
    }

    protected class AddressRefreshRetryTask implements Runnable {
        private final RetryServiceInstancesChangedEvent retryEvent;
        private final Semaphore retryPermission;

        public AddressRefreshRetryTask(Semaphore semaphore, String serviceName) {
            this.retryEvent = new RetryServiceInstancesChangedEvent(serviceName);
            this.retryPermission = semaphore;
        }

        @Override
        public void run() {
            retryPermission.release();
            ServiceInstancesChangedListener.this.onEvent(retryEvent);
        }
    }

    private void refreshInvoker(List<URL> invokerUrls) {
        Assert.notNull(invokerUrls, "invokerUrls should not be null, use empty url list to clear address.");

        if (invokerUrls.size() == 0) {
            logger.info("Received empty url list...");
            this.addressToInvoker = Collections.emptyMap();
            destroyAllInvokers(); // Close all invokers
        } else {
            Map<String, Invoker<?>> oldUrlInvokerMap = this.addressToInvoker; // local reference
            if (CollectionUtils.isEmpty(invokerUrls)) {
                return;
            }

            Map<String, Invoker<?>> newAddressInvokerMap = toInvokers(invokerUrls);// Translate url list to Invoker map
            logger.info("Refreshed invoker size " + newAddressInvokerMap.size());

            if (CollectionUtils.isEmptyMap(newAddressInvokerMap)) {
                logger.error(new IllegalStateException("Cannot create invokers from url address list (total " + invokerUrls.size() + ")"));
                return;
            }
            this.addressToInvoker = newAddressInvokerMap;

            if (oldUrlInvokerMap != null) {
                try {
                    destroyUnusedInvokers(oldUrlInvokerMap, newAddressInvokerMap); // Close the unused Invoker
                } catch (Exception e) {
                    logger.warn("destroyUnusedInvokers error. ", e);
                }
            }
        }

        // notify invokers refreshed
        this.invokersChanged();
    }

    protected void invokersChanged() {
        // notify directory invokers changed
    }

    /**
     * Turn urls into invokers, and if url has been refer, will not re-reference.
     *
     * @param urls
     * @return invokers
     */
    private Map<String, Invoker<?>> toInvokers(List<URL> urls) {
        Map<String, Invoker<?>> newAddressInvokerMap = new HashMap<>();
        if (urls == null || urls.isEmpty()) {
            return newAddressInvokerMap;
        }
        for (URL url : urls) {
            InstanceAddressURL instanceAddressURL = (InstanceAddressURL) url;
            if (EMPTY_PROTOCOL.equals(instanceAddressURL.getProtocol())) {
                continue;
            }
            if (!ExtensionLoader.getExtensionLoader(Protocol.class).hasExtension(instanceAddressURL.getProtocol())) {
                logger.error(new IllegalStateException("Unsupported protocol " + instanceAddressURL.getProtocol() +
                    " in notified url: " + instanceAddressURL + " from registry " + getUrl().getAddress() +
                    " to consumer " + NetUtils.getLocalHost() + ", supported protocol: " +
                    ExtensionLoader.getExtensionLoader(Protocol.class).getSupportedExtensions()));
                continue;
            }

            Invoker<?> invoker = addressToInvoker == null ? null : addressToInvoker.get(instanceAddressURL.getAddress());
            if (invoker == null || urlChanged(invoker, instanceAddressURL)) { // Not in the cache, refer again
                try {
                    boolean enabled = true;
                    if (instanceAddressURL.hasParameter(DISABLED_KEY)) {
                        enabled = !instanceAddressURL.getParameter(DISABLED_KEY, false);
                    } else {
                        enabled = instanceAddressURL.getParameter(ENABLED_KEY, true);
                    }
                    if (enabled) {
                        invoker = protocol.refer(Object.class, instanceAddressURL);// FIXME, multi protocol invoker
                    }
                } catch (Throwable t) {
                    logger.error("Failed to refer invoker for url:(" + instanceAddressURL + ")" + t.getMessage(), t);
                }
                if (invoker != null) { // Put new invoker in cache
                    newAddressInvokerMap.put(instanceAddressURL.getAddress(), invoker);
                }
            } else {
                newAddressInvokerMap.put(instanceAddressURL.getAddress(), invoker);
                addressToInvoker.remove(instanceAddressURL.getAddress(), invoker);
            }
        }
        return newAddressInvokerMap;
    }

    /**
     * Close all invokers
     */
    protected void destroyAllInvokers() {
        Map<String, Invoker<?>> localUrlInvokerMap = this.addressToInvoker; // local reference
        if (localUrlInvokerMap != null) {
            for (Invoker<?> invoker : new ArrayList<>(localUrlInvokerMap.values())) {
                try {
                    invoker.destroy();
                } catch (Throwable t) {
                    logger.warn("Failed to destroy invoker to provider " + invoker.getUrl(), t);
                }
            }
            localUrlInvokerMap.clear();
        }

        this.addressToInvoker = null;
    }

    /**
     * Check whether the invoker in the cache needs to be destroyed
     * If set attribute of url: refer.autodestroy=false, the invokers will only increase without decreasing,there may be a refer leak
     *
     * @param oldUrlInvokerMap
     * @param newUrlInvokerMap
     */
    private void destroyUnusedInvokers(Map<String, Invoker<?>> oldUrlInvokerMap, Map<String, Invoker<?>> newUrlInvokerMap) {
        if (newUrlInvokerMap == null || newUrlInvokerMap.size() == 0) {
            destroyAllInvokers();
            return;
        }

        if (oldUrlInvokerMap == null || oldUrlInvokerMap.size() == 0) {
            return;
        }

        for (Map.Entry<String, Invoker<?>> entry : oldUrlInvokerMap.entrySet()) {
            Invoker<?> invoker = entry.getValue();
            if (invoker != null) {
                try {
                    invoker.destroy();
                    if (logger.isDebugEnabled()) {
                        logger.debug("destroy invoker[" + invoker.getUrl() + "] success. ");
                    }
                } catch (Exception e) {
                    logger.warn("destroy invoker[" + invoker.getUrl() + "] failed. " + e.getMessage(), e);
                }
            }
        }
        logger.info(oldUrlInvokerMap.size() + " deprecated invokers deleted.");
    }

    private boolean urlChanged(Invoker<?> invoker, InstanceAddressURL newURL) {
        InstanceAddressURL oldURL = (InstanceAddressURL) invoker.getUrl();

        return !newURL.getInstance().equals(oldURL.getInstance());
    }


}
