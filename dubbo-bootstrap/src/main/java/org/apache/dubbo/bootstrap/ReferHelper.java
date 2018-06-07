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
import com.alibaba.dubbo.common.bytecode.Wrapper;
import com.alibaba.dubbo.common.extension.ExtensionLoader;
import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.common.utils.ConfigUtils;
import com.alibaba.dubbo.common.utils.NetUtils;
import com.alibaba.dubbo.common.utils.StringUtils;
import com.alibaba.dubbo.config.ApplicationConfig;
import com.alibaba.dubbo.config.ConsumerConfig;
import com.alibaba.dubbo.config.MethodConfig;
import com.alibaba.dubbo.config.ModuleConfig;
import com.alibaba.dubbo.config.MonitorConfig;
import com.alibaba.dubbo.config.ReferenceConfig;
import com.alibaba.dubbo.config.model.ApplicationModel;
import com.alibaba.dubbo.config.model.ConsumerModel;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Protocol;
import com.alibaba.dubbo.rpc.ProxyFactory;
import com.alibaba.dubbo.rpc.StaticContext;
import com.alibaba.dubbo.rpc.cluster.Cluster;
import com.alibaba.dubbo.rpc.cluster.directory.StaticDirectory;
import com.alibaba.dubbo.rpc.cluster.support.AvailableCluster;
import com.alibaba.dubbo.rpc.cluster.support.ClusterUtils;
import com.alibaba.dubbo.rpc.protocol.injvm.InjvmProtocol;
import com.alibaba.dubbo.rpc.service.GenericService;
import com.alibaba.dubbo.rpc.support.ProtocolUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static com.alibaba.dubbo.common.utils.NetUtils.isInvalidLocalHost;

/**
 *
 */
public class ReferHelper {
    private static final Logger logger = LoggerFactory.getLogger(ReferHelper.class);

    private static final Protocol refprotocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();
    private static final Cluster cluster = ExtensionLoader.getExtensionLoader(Cluster.class).getAdaptiveExtension();
    private static final ProxyFactory proxyFactory = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();
    private final List<URL> urls = new ArrayList<URL>();
    private final Map<ReferenceConfig, Invoker<?>> invokers = new HashMap<>();

    private DubboBootstrap bootstrap;
    private ReferenceConfig referenceConfig;

    public ReferHelper(DubboBootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    public void setReferenceConfig(ReferenceConfig referenceConfig) {
        this.referenceConfig = referenceConfig;
    }

    public Object refer() {
        if (referenceConfig.getRef() == null) {
            init();
        }
        return referenceConfig.getRef();
    }

    private void init() {
        if (invokers.get(referenceConfig) != null) {
            return;
        }
        initReferenceConfig();
        referenceConfig.setInitialized(true);
        String interfaceName = referenceConfig.getInterface();
        if (interfaceName == null || interfaceName.length() == 0) {
            throw new IllegalStateException("<dubbo:reference interface=\"\" /> interface not allow null!");
        }
        ConsumerConfig consumer = referenceConfig.getConsumer();
        if (referenceConfig.getGeneric() == null && consumer.getGeneric() != null) {
            referenceConfig.setGeneric(consumer.getGeneric());
        }
        if (ProtocolUtils.isGeneric(referenceConfig.getGeneric())) {
            referenceConfig.setInterface(GenericService.class);
        } else {
            try {
                referenceConfig.setInterface(Class.forName(interfaceName, true, Thread.currentThread()
                        .getContextClassLoader()));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
            referenceConfig.checkInterfaceAndMethods();
        }
        String resolve = System.getProperty(interfaceName);
        String resolveFile = null;
        if (resolve == null || resolve.length() == 0) {
            resolveFile = System.getProperty("dubbo.resolve.file");
            if (resolveFile == null || resolveFile.length() == 0) {
                File userResolveFile = new File(new File(System.getProperty("user.home")), "dubbo-resolve.properties");
                if (userResolveFile.exists()) {
                    resolveFile = userResolveFile.getAbsolutePath();
                }
            }
            if (resolveFile != null && resolveFile.length() > 0) {
                Properties properties = new Properties();
                FileInputStream fis = null;
                try {
                    fis = new FileInputStream(new File(resolveFile));
                    properties.load(fis);
                } catch (IOException e) {
                    throw new IllegalStateException("Unload " + resolveFile + ", cause: " + e.getMessage(), e);
                } finally {
                    try {
                        if (null != fis) fis.close();
                    } catch (IOException e) {
                        logger.warn(e.getMessage(), e);
                    }
                }
                resolve = properties.getProperty(interfaceName);
            }
        }
        if (resolve != null && resolve.length() > 0) {
            referenceConfig.setUrl(resolve);
            if (logger.isWarnEnabled()) {
                if (resolveFile != null) {
                    logger.warn("Using default dubbo resolve file " + resolveFile + " replace " + interfaceName + "" + resolve + " to p2p invoke remote service.");
                } else {
                    logger.warn("Using -D" + interfaceName + "=" + resolve + " to p2p invoke remote service.");
                }
            }
        }
        Map<String, String> map = new HashMap<String, String>();
        Map<Object, Object> attributes = new HashMap<Object, Object>();
        map.put(Constants.SIDE_KEY, Constants.CONSUMER_SIDE);
        map.put(Constants.DUBBO_VERSION_KEY, Version.getProtocolVersion());
        map.put(Constants.TIMESTAMP_KEY, String.valueOf(System.currentTimeMillis()));
        if (ConfigUtils.getPid() > 0) {
            map.put(Constants.PID_KEY, String.valueOf(ConfigUtils.getPid()));
        }

        Class<?> interfaceClass = referenceConfig.getInterfaceClass();
        if (!referenceConfig.isGeneric()) {
            String revision = Version.getVersion(interfaceClass, referenceConfig.getVersion());
            if (revision != null && revision.length() > 0) {
                map.put("revision", revision);
            }

            String[] methods = Wrapper.getWrapper(interfaceClass).getMethodNames();
            if (methods.length == 0) {
                logger.warn("NO method found in service interface " + interfaceClass.getName());
                map.put("methods", Constants.ANY_VALUE);
            } else {
                map.put("methods", StringUtils.join(new HashSet<String>(Arrays.asList(methods)), ","));
            }
        }
        map.put(Constants.INTERFACE_KEY, interfaceName);
        map.putAll(BootstrapUtils.configToMap(referenceConfig.getApplication(), null));
        map.putAll(BootstrapUtils.configToMap(referenceConfig.getModule(), null));
        map.putAll(BootstrapUtils.configToMap(referenceConfig.getConsumer(), Constants.DEFAULT_KEY));
        map.putAll(BootstrapUtils.configToMap(referenceConfig, null));
        String prefix = StringUtils.getServiceKey(map);

        List<MethodConfig> methodConfigs = referenceConfig.getMethods();
        if (methodConfigs != null && !methodConfigs.isEmpty()) {
            for (MethodConfig methodConfig : methodConfigs) {
                map.putAll(BootstrapUtils.configToMap(methodConfig, methodConfig.getName()));
                String retryKey = methodConfig.getName() + ".retry";
                if (map.containsKey(retryKey)) {
                    String retryValue = map.remove(retryKey);
                    if ("false".equals(retryValue)) {
                        map.put(methodConfig.getName() + ".retries", "0");
                    }
                }
                referenceConfig.appendAttributes(attributes, methodConfig, prefix + "." + methodConfig.getName());
                referenceConfig.checkAndConvertImplicitConfig(methodConfig, map, attributes);
            }
        }

        String hostToRegistry = ConfigUtils.getSystemProperty(Constants.DUBBO_IP_TO_REGISTRY);
        if (hostToRegistry == null || hostToRegistry.length() == 0) {
            hostToRegistry = NetUtils.getLocalHost();
        } else if (isInvalidLocalHost(hostToRegistry)) {
            throw new IllegalArgumentException("Specified invalid registry ip from property:" + Constants.DUBBO_IP_TO_REGISTRY + ", value:" + hostToRegistry);
        }
        map.put(Constants.REGISTER_IP_KEY, hostToRegistry);

        //attributes are stored by system context.
        StaticContext.getSystemContext().putAll(attributes);
        referenceConfig.setRef(createProxy(map));
        // TODO
        ConsumerModel consumerModel = new ConsumerModel(referenceConfig.getUniqueServiceName(), referenceConfig, referenceConfig.getRef(), interfaceClass.getMethods());
        ApplicationModel.initConsumerModel(referenceConfig.getUniqueServiceName(), consumerModel);
    }

    @SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
    private Object createProxy(Map<String, String> map) {
        URL tmpUrl = new URL("temp", "localhost", 0, map);
        final boolean isJvmRefer;
        String url = referenceConfig.getUrl();
        if (referenceConfig.isInjvm() == null) {
            if (url != null && url.length() > 0) { // if a url is specified, don't do local reference
                isJvmRefer = false;
            } else if (InjvmProtocol.getInjvmProtocol().isInjvmRefer(tmpUrl)) {
                // by default, reference local service if there is
                isJvmRefer = true;
            } else {
                isJvmRefer = false;
            }
        } else {
            isJvmRefer = referenceConfig.isInjvm().booleanValue();
        }

        Class<?> interfaceClass = referenceConfig.getInterfaceClass();
        Invoker<?> invoker = null;
        if (isJvmRefer) {
            URL dubboUrl = new URL(Constants.LOCAL_PROTOCOL, NetUtils.LOCALHOST, 0, interfaceClass.getName()).addParameters(map);
            invoker = refprotocol.refer(interfaceClass, dubboUrl);
            if (logger.isInfoEnabled()) {
                logger.info("Using injvm service " + interfaceClass.getName());
            }
        } else {
            if (url != null && url.length() > 0) { // user specified URL, could be peer-to-peer address, or register center's address.
                String[] us = Constants.SEMICOLON_SPLIT_PATTERN.split(url);
                if (us != null && us.length > 0) {
                    for (String u : us) {
                        URL dubboUrl = URL.valueOf(u);
                        if (dubboUrl.getPath() == null || dubboUrl.getPath().length() == 0) {
                            dubboUrl = dubboUrl.setPath(referenceConfig.getInterface());
                        }
                        if (Constants.REGISTRY_PROTOCOL.equals(dubboUrl.getProtocol())) {
                            urls.add(dubboUrl.addParameterAndEncoded(Constants.REFER_KEY, StringUtils.toQueryString(map)));
                        } else {
                            urls.add(ClusterUtils.mergeUrl(dubboUrl, map));
                        }
                    }
                }
            } else { // assemble URL from register center's configuration
                List<URL> us = referenceConfig.loadRegistries(false);
                if (us != null && !us.isEmpty()) {
                    for (URL u : us) {
                        URL monitorUrl = referenceConfig.loadMonitor(u);
                        if (monitorUrl != null) {
                            map.put(Constants.MONITOR_KEY, URL.encode(monitorUrl.toFullString()));
                        }
                        urls.add(u.addParameterAndEncoded(Constants.REFER_KEY, StringUtils.toQueryString(map)));
                    }
                }
                if (urls.isEmpty()) {
                    throw new IllegalStateException("No such any registry to reference " + referenceConfig.getInterface() + " on the consumer " + NetUtils.getLocalHost() + " use dubbo version " + Version.getVersion() + ", please config <dubbo:registry address=\"...\" /> to your spring config.");
                }
            }

            if (urls.size() == 1) {
                invoker = refprotocol.refer(interfaceClass, urls.get(0));
            } else {
                List<Invoker<?>> invokers = new ArrayList<Invoker<?>>();
                URL registryURL = null;
                for (URL dubboUrl : urls) {
                    invokers.add(refprotocol.refer(interfaceClass, dubboUrl));
                    if (Constants.REGISTRY_PROTOCOL.equals(dubboUrl.getProtocol())) {
                        registryURL = dubboUrl; // use last registry url
                    }
                }
                if (registryURL != null) { // registry url is available
                    // use AvailableCluster only when register's cluster is available
                    URL u = registryURL.addParameter(Constants.CLUSTER_KEY, AvailableCluster.NAME);
                    invoker = cluster.join(new StaticDirectory(u, invokers));
                } else { // not a registry url
                    invoker = cluster.join(new StaticDirectory(invokers));
                }
            }
        }

        Boolean c = referenceConfig.isCheck();
        if (c == null && referenceConfig.getConsumer() != null) {
            c = referenceConfig.getConsumer().isCheck();
        }
        if (c == null) {
            c = true; // default true
        }
        if (c && !invoker.isAvailable()) {
            throw new IllegalStateException("Failed to check the status of the service " + referenceConfig.getInterface() + ". No provider available for the service " + (referenceConfig.getVersion() == null ? "" : referenceConfig.getGroup() + "/") + referenceConfig.getInterface() + (referenceConfig.getVersion() == null ? "" : ":" + referenceConfig.getVersion()) + " from the url " + invoker.getUrl() + " to the consumer " + NetUtils.getLocalHost() + " use dubbo version " + Version.getVersion());
        }
        if (logger.isInfoEnabled()) {
            logger.info("Refer dubbo service " + interfaceClass.getName() + " from url " + invoker.getUrl());
        }

        invokers.put(referenceConfig, invoker);
        // create service proxy
        return proxyFactory.getProxy(invoker);
    }

    public void initReferenceConfig() {
        if (referenceConfig.getConsumer() == null) {
            referenceConfig.setConsumer(bootstrap.getConsumer() == null ? new ConsumerConfig() : bootstrap.getConsumer());
        }
        if (referenceConfig.getModule() == null) {
            referenceConfig.setModule(bootstrap.getModule());
        }
        if (referenceConfig.getApplication() == null) {
            referenceConfig.setApplication(bootstrap.getApplication() == null ? new ApplicationConfig() : bootstrap.getApplication());
        }
        if (referenceConfig.getMonitor() == null) {
            referenceConfig.setMonitor(bootstrap.getMonitor() == null ? new MonitorConfig() : bootstrap.getMonitor());
        }
        if (referenceConfig.getRegistries() == null) {
            referenceConfig.setRegistries(bootstrap.getRegistries());
        }


        ConsumerConfig consumer = referenceConfig.getConsumer();
        if (consumer != null) {
            if (referenceConfig.getApplication() == null) {
                referenceConfig.setApplication(consumer.getApplication());
            }
            if (referenceConfig.getModule() == null) {
                referenceConfig.setModule(consumer.getModule());
            }
            if (referenceConfig.getRegistries() == null) {
                referenceConfig.setRegistries(consumer.getRegistries());
            }
            if (referenceConfig.getMonitor() == null) {
                referenceConfig.setMonitor(consumer.getMonitor());
            }
        }
        ModuleConfig module = referenceConfig.getModule();
        if (module != null) {
            if (referenceConfig.getRegistries() == null) {
                referenceConfig.setRegistries(module.getRegistries());
            }
            if (referenceConfig.getMonitor() == null) {
                referenceConfig.setMonitor(module.getMonitor());
            }
        }
        ApplicationConfig application = referenceConfig.getApplication();
        if (application != null) {
            if (referenceConfig.getRegistries() == null) {
                referenceConfig.setRegistries(application.getRegistries());
            }
            if (referenceConfig.getMonitor() == null) {
                referenceConfig.setMonitor(application.getMonitor());
            }
        }

        referenceConfig.checkApplication();
        referenceConfig.checkStubAndMock(referenceConfig.getInterfaceClass());
    }

    public void unrefer() {
        invokers.keySet().forEach(this::unrefer);
        invokers.clear();
    }

    public void unrefer(ReferenceConfig config) {
        Invoker<?> invoker = invokers.remove(config);
        invoker.destroy();
    }
}
