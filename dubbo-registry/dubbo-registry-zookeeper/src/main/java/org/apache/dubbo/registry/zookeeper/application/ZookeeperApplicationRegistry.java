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
package org.apache.dubbo.registry.zookeeper.application;

import org.apache.dubbo.common.Constants;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.UrlUtils;
import org.apache.dubbo.registry.NotifyListener;
import org.apache.dubbo.registry.application.MetadataService;
import org.apache.dubbo.registry.support.FailbackRegistry;
import org.apache.dubbo.remoting.zookeeper.StateListener;
import org.apache.dubbo.remoting.zookeeper.ZookeeperClient;
import org.apache.dubbo.remoting.zookeeper.ZookeeperTransporter;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Protocol;
import org.apache.dubbo.rpc.ProxyFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 *
 */
public class ZookeeperApplicationRegistry extends FailbackRegistry {

    private final static int DEFAULT_ZOOKEEPER_PORT = 2181;

    private final static String DEFAULT_ROOT = "dubbo";

    private final String root;

    private final ZookeeperClient zkClient;

    public ZookeeperApplicationRegistry(URL url, ZookeeperTransporter zookeeperTransporter) {
        super(url);
        if (url.isAnyHost()) {
            throw new IllegalStateException("registry address == null");
        }
        String group = url.getParameter(Constants.GROUP_KEY, DEFAULT_ROOT);
        if (!group.startsWith(Constants.PATH_SEPARATOR)) {
            group = Constants.PATH_SEPARATOR + group;
        }
        this.root = group;
        zkClient = zookeeperTransporter.connect(url);
        zkClient.addStateListener(state -> {
            if (state == StateListener.RECONNECTED) {
                try {
                    recover();
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }
            }
        });
    }

    @Override
    public void doRegister(URL url) {

    }

    @Override
    public void doUnregister(URL url) {

    }

    @Override
    public void doSubscribe(URL url, NotifyListener listener) {

    }

    @Override
    public void doUnsubscribe(URL url, NotifyListener listener) {

    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    class ApplicationAddressListener implements NotifyListener {

        private Protocol protocol;

        private ProxyFactory proxyFactory;

        private Map<String, List<URL>> appURLs = new ConcurrentHashMap<>();

        // <app, <interface, listener>>
        private Map<String, Map<String, NotifyListener>> listeners = new ConcurrentHashMap<>();

//        private Map<String, MetadataService> services = new ConcurrentHashMap<>();

        /**
         * @param urls list of instances and each instance's metadata, organized by application.
         */
        @Override
        public synchronized void notify(List<URL> urls) {
            if (CollectionUtils.isEmpty(urls)) {
                throw new IllegalStateException("Please use URL with empty:// protocol to clear addresses.");
            }

            if (UrlUtils.isEmptyProtocol(urls)) {
                String subscribeApp = urls.get(0).getParameter(Constants.APPLICATION_KEY);
                listeners.get(subscribeApp).notify(urls); // if empty://, use the app urls directly.
                return;
            }

            appURLs.put(urls.get(0).getPath(), urls);

            listeners.forEach((app, serviceListeners) -> {
                serviceListeners.forEach((s, listener) -> {
                    notifyService(app, s, listener);
                });
            });
        }

        public void addListener(URL url, NotifyListener serviceListener) {
            String subscribeApp = url.getParameter(); // get the provider application this service/url belongs to
            Map<String, NotifyListener> serviceListeners = listeners.computeIfAbsent(subscribeApp, (app) -> new ConcurrentHashMap<>());
            serviceListeners.put(url.getServiceKey(), serviceListener);
            notifyService(subscribeApp, url.getServiceKey(), serviceListener);
        }

        private void notifyService(String app, String service, NotifyListener listener) {
            MetadataService metadataService = getMetadataService(app);
            String metadata = metadataService.getExportedURLs(service);
            // generate service urls
            List<URL> serviceUrls = appURLs.get(app).stream()
                    .map(appUrl -> //appUrl + metadata)
                            .collect(Collectors.toList());
            listener.notify(serviceUrls);
        }

        private MetadataService getMetadataService(String app) {
            Invoker<MetadataService> invoker = protocol.refer(MetadataService.class, metadataUrl);
            return proxyFactory.getProxy(invoker);
        }
    }
}
