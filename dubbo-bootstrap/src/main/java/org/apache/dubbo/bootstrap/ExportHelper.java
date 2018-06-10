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
import com.alibaba.dubbo.common.utils.ClassHelper;
import com.alibaba.dubbo.common.utils.ConfigUtils;
import com.alibaba.dubbo.common.utils.NamedThreadFactory;
import com.alibaba.dubbo.common.utils.StringUtils;
import com.alibaba.dubbo.config.ApplicationConfig;
import com.alibaba.dubbo.config.ArgumentConfig;
import com.alibaba.dubbo.config.MethodConfig;
import com.alibaba.dubbo.config.ModuleConfig;
import com.alibaba.dubbo.config.MonitorConfig;
import com.alibaba.dubbo.config.ProtocolConfig;
import com.alibaba.dubbo.config.ProviderConfig;
import com.alibaba.dubbo.config.ServiceConfig;
import com.alibaba.dubbo.config.invoker.DelegateProviderMetaDataInvoker;
import com.alibaba.dubbo.config.model.ApplicationModel;
import com.alibaba.dubbo.config.model.ProviderModel;
import com.alibaba.dubbo.rpc.Exporter;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Protocol;
import com.alibaba.dubbo.rpc.ProxyFactory;
import com.alibaba.dubbo.rpc.ServiceClassHolder;
import com.alibaba.dubbo.rpc.cluster.ConfiguratorFactory;
import com.alibaba.dubbo.rpc.service.GenericService;
import com.alibaba.dubbo.rpc.support.ProtocolUtils;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.alibaba.dubbo.common.utils.NetUtils.LOCALHOST;
import static com.alibaba.dubbo.common.utils.NetUtils.getAvailablePort;
import static com.alibaba.dubbo.common.utils.NetUtils.getLocalHost;
import static com.alibaba.dubbo.common.utils.NetUtils.isInvalidLocalHost;
import static com.alibaba.dubbo.common.utils.NetUtils.isInvalidPort;

/**
 *
 */
public class ExportHelper {
    private static final Logger logger = LoggerFactory.getLogger(ExportHelper.class);
    private static final Protocol protocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();
    private static final ProxyFactory proxyFactory = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();
    private static final ScheduledExecutorService delayExportExecutor = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("DubboServiceDelayExporter", true));
    private final Map<ServiceConfig, List<Exporter<?>>> exporters = new HashMap<>();
    private static final Map<String, Integer> RANDOM_PORT_MAP = new HashMap<>();
    private DubboBootstrap bootstrap;
    private ServiceConfig serviceConfig;

    public ExportHelper(DubboBootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    public void setServiceConfig(ServiceConfig serviceConfig) {
        this.serviceConfig = serviceConfig;
    }

    public void export() {
        initConfig(bootstrap);
        Boolean export = serviceConfig.getExport();
        Integer delay = serviceConfig.getDelay();
        ProviderConfig provider = serviceConfig.getProvider();
        if (provider != null) {
            if (export == null) {
                export = provider.getExport();
            }
            if (delay == null) {
                delay = provider.getDelay();
            }
        }
        if (export != null && !export) {
            return;
        }

        if (delay != null && delay > 0) {
            delayExportExecutor.schedule(new Runnable() {
                @Override
                public void run() {
                    doExport();
                }
            }, delay, TimeUnit.MILLISECONDS);
        } else {
            doExport();
        }
    }

    protected void doExport() {
        if (exporters.get(serviceConfig) != null) {
            return;
        }
        if (serviceConfig.getInterface() == null || serviceConfig.getInterface().length() == 0) {
            throw new IllegalStateException("<dubbo:service interface=\"\" /> interface not allow null!");
        }
        if (serviceConfig.getRef() instanceof GenericService) {
            serviceConfig.setInterface(GenericService.class);
            if (StringUtils.isEmpty(serviceConfig.getGeneric())) {
                serviceConfig.setGeneric(Boolean.TRUE.toString());
            }
        } else {
            try {
                serviceConfig.setInterface(Class.forName(serviceConfig.getInterface(), true, Thread.currentThread()
                        .getContextClassLoader()));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
            serviceConfig.checkInterfaceAndMethods();
            serviceConfig.checkRef();
//            serviceConfig.setGeneric(Boolean.FALSE.toString());
        }

        String local = serviceConfig.getLocal();
        if (local != null) {
            if ("true".equals(local)) {
                local = serviceConfig.getInterface() + "Local";
            }
            Class<?> localClass;
            try {
                localClass = ClassHelper.forNameWithThreadContextClassLoader(local);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
            if (!serviceConfig.getInterfaceClass().isAssignableFrom(localClass)) {
                throw new IllegalStateException("The local implementation class " + localClass.getName() + " not implement interface " + serviceConfig.getInterface());
            }
            serviceConfig.setLocal(local);
        }
        String stub = serviceConfig.getStub();
        if (stub != null) {
            if ("true".equals(stub)) {
                stub = serviceConfig.getInterface() + "Stub";
            }
            Class<?> stubClass;
            try {
                stubClass = ClassHelper.forNameWithThreadContextClassLoader(stub);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
            if (!serviceConfig.getInterfaceClass().isAssignableFrom(stubClass)) {
                throw new IllegalStateException("The stub implementation class " + stubClass.getName() + " not implement interface " + serviceConfig.getInterface());
            }
            serviceConfig.setStub(stub);
        }
//        appendProperties(this);
        // TODO local and stub still may not append yet, so find a way to check.
//        checkStubAndMock(interfaceClass);
        String path = serviceConfig.getPath();
        if (path == null || path.length() == 0) {
            serviceConfig.setPath(serviceConfig.getInterface());
        }
        doExportUrls();

        // TODO
        ProviderModel providerModel = new ProviderModel(serviceConfig.getUniqueServiceName(), serviceConfig, serviceConfig.getRef());
        ApplicationModel.initProviderModel(serviceConfig.getUniqueServiceName(), providerModel);
    }

    private void doExportUrls() {
        List<URL> registryURLs = BootstrapUtils.loadRegistries(serviceConfig, true);
        for (ProtocolConfig protocolConfig : serviceConfig.getProtocols()) {
            doExportUrlsFor1Protocol(protocolConfig, registryURLs);
        }
    }

    private void doExportUrlsFor1Protocol(ProtocolConfig protocolConfig, List<URL> registryURLs) {
        String name = protocolConfig.getName();
        if (name == null || name.length() == 0) {
            name = "dubbo";
        }

        Map<String, String> map = new HashMap<String, String>();
        map.put(Constants.SIDE_KEY, Constants.PROVIDER_SIDE);
        map.put(Constants.DUBBO_VERSION_KEY, Version.getProtocolVersion());
        map.put(Constants.TIMESTAMP_KEY, String.valueOf(System.currentTimeMillis()));
        if (ConfigUtils.getPid() > 0) {
            map.put(Constants.PID_KEY, String.valueOf(ConfigUtils.getPid()));
        }
        map.putAll(BootstrapUtils.configToMap(serviceConfig.getApplication(), null));
        map.putAll(BootstrapUtils.configToMap(serviceConfig.getModule(), null));
        map.putAll(BootstrapUtils.configToMap(serviceConfig.getProvider(), Constants.DEFAULT_KEY));
        map.putAll(BootstrapUtils.configToMap(protocolConfig, null));
        map.putAll(BootstrapUtils.configToMap(serviceConfig, null));
        List<MethodConfig> methodConfigs = serviceConfig.getMethods();
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
                List<ArgumentConfig> arguments = methodConfig.getArguments();
                if (arguments != null && !arguments.isEmpty()) {
                    for (ArgumentConfig argument : arguments) {
                        // convert argument type
                        if (argument.getType() != null && argument.getType().length() > 0) {
                            Method[] methods = serviceConfig.getInterfaceClass().getMethods();
                            // visit all methods
                            if (methods != null && methods.length > 0) {
                                for (int i = 0; i < methods.length; i++) {
                                    String methodName = methods[i].getName();
                                    // target the method, and get its signature
                                    if (methodName.equals(methodConfig.getName())) {
                                        Class<?>[] argtypes = methods[i].getParameterTypes();
                                        // one callback in the method
                                        if (argument.getIndex() != -1) {
                                            if (argtypes[argument.getIndex()].getName().equals(argument.getType())) {
                                                map.putAll(BootstrapUtils.configToMap(argument, methodConfig.getName() + "." + argument.getIndex()));
                                            } else {
                                                throw new IllegalArgumentException("argument config error : the index attribute and type attribute not match :index :" + argument.getIndex() + ", type:" + argument.getType());
                                            }
                                        } else {
                                            // multiple callbacks in the method
                                            for (int j = 0; j < argtypes.length; j++) {
                                                Class<?> argclazz = argtypes[j];
                                                if (argclazz.getName().equals(argument.getType())) {
                                                    map.putAll(BootstrapUtils.configToMap(argument, methodConfig.getName() + "." + j));
                                                    if (argument.getIndex() != -1 && argument.getIndex() != j) {
                                                        throw new IllegalArgumentException("argument config error : the index attribute and type attribute not match :index :" + argument.getIndex() + ", type:" + argument.getType());
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (argument.getIndex() != -1) {
                            map.putAll(BootstrapUtils.configToMap(argument, methodConfig.getName() + "." + argument.getIndex()));
                        } else {
                            throw new IllegalArgumentException("argument config must set index or type attribute.eg: <dubbo:argument index='0' .../> or <dubbo:argument type=xxx .../>");
                        }

                    }
                }
            } // end of methods for
        }

        Class<?> interfaceClass = serviceConfig.getInterfaceClass();
        if (ProtocolUtils.isGeneric(serviceConfig.getGeneric())) {
            map.put(Constants.GENERIC_KEY, serviceConfig.getGeneric());
            map.put(Constants.METHODS_KEY, Constants.ANY_VALUE);
        } else {
            String revision = Version.getVersion(interfaceClass, serviceConfig.getVersion());
            if (revision != null && revision.length() > 0) {
                map.put("revision", revision);
            }

            String[] methods = Wrapper.getWrapper(interfaceClass).getMethodNames();
            if (methods.length == 0) {
                logger.warn("NO method found in service interface " + interfaceClass.getName());
                map.put(Constants.METHODS_KEY, Constants.ANY_VALUE);
            } else {
                map.put(Constants.METHODS_KEY, StringUtils.join(new HashSet<String>(Arrays.asList(methods)), ","));
            }
        }
        String token = serviceConfig.getToken();
        if (!ConfigUtils.isEmpty(token)) {
            if (ConfigUtils.isDefault(token)) {
                map.put(Constants.TOKEN_KEY, UUID.randomUUID().toString());
            } else {
                map.put(Constants.TOKEN_KEY, token);
            }
        }
        if (Constants.LOCAL_PROTOCOL.equals(protocolConfig.getName())) {
            protocolConfig.setRegister(false);
            map.put("notify", "false");
        }
        // export service
        ProviderConfig provider = serviceConfig.getProvider();
        String contextPath = protocolConfig.getContextpath();
        if ((contextPath == null || contextPath.length() == 0) && provider != null) {
            contextPath = provider.getContextpath();
        }

        String host = this.findConfigedHosts(protocolConfig, registryURLs, map);
        Integer port = this.findConfigedPorts(protocolConfig, map);
        URL url = new URL(name, host, port, (contextPath == null || contextPath.length() == 0 ? "" : contextPath + "/") + serviceConfig.getPath(), map);

        if (ExtensionLoader.getExtensionLoader(ConfiguratorFactory.class)
                .hasExtension(url.getProtocol())) {
            url = ExtensionLoader.getExtensionLoader(ConfiguratorFactory.class)
                    .getExtension(url.getProtocol()).getConfigurator(url).configure(url);
        }

        String scope = url.getParameter(Constants.SCOPE_KEY);
        // don't export when none is configured
        if (!Constants.SCOPE_NONE.toString().equalsIgnoreCase(scope)) {

            // export to local if the config is not remote (export to remote only when config is remote)
            if (!Constants.SCOPE_REMOTE.toString().equalsIgnoreCase(scope)) {
                exportLocal(url);
            }
            // export to remote if the config is not local (export to local only when config is local)
            if (!Constants.SCOPE_LOCAL.toString().equalsIgnoreCase(scope)) {
                if (logger.isInfoEnabled()) {
                    logger.info("Export dubbo service " + interfaceClass.getName() + " to url " + url);
                }
                if (registryURLs != null && !registryURLs.isEmpty()) {
                    for (URL registryURL : registryURLs) {
                        url = url.addParameterIfAbsent(Constants.DYNAMIC_KEY, registryURL.getParameter(Constants.DYNAMIC_KEY));
                        URL monitorUrl = BootstrapUtils.loadMonitor(serviceConfig, registryURL);
                        if (monitorUrl != null) {
                            url = url.addParameterAndEncoded(Constants.MONITOR_KEY, monitorUrl.toFullString());
                        }
                        if (logger.isInfoEnabled()) {
                            logger.info("Register dubbo service " + interfaceClass.getName() + " url " + url + " to registry " + registryURL);
                        }

                        // For providers, this is used to enable custom proxy to generate invoker
                        String proxy = url.getParameter(Constants.PROXY_KEY);
                        if (StringUtils.isNotEmpty(proxy)) {
                            registryURL = registryURL.addParameter(Constants.PROXY_KEY, proxy);
                        }

                        Invoker<?> invoker = proxyFactory.getInvoker(serviceConfig.getRef(), (Class) interfaceClass, registryURL.addParameterAndEncoded(Constants.EXPORT_KEY, url.toFullString()));
                        DelegateProviderMetaDataInvoker wrapperInvoker = new DelegateProviderMetaDataInvoker(invoker, serviceConfig);

                        Exporter<?> exporter = protocol.export(wrapperInvoker);
                        saveExporter(exporter);
                    }
                } else {
                    Invoker<?> invoker = proxyFactory.getInvoker(serviceConfig.getRef(), (Class) interfaceClass, url);
                    DelegateProviderMetaDataInvoker wrapperInvoker = new DelegateProviderMetaDataInvoker(invoker, serviceConfig);

                    Exporter<?> exporter = protocol.export(wrapperInvoker);
                    saveExporter(exporter);
                }
            }
        }
        serviceConfig.getExportedUrls().add(url);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void exportLocal(URL url) {
        if (!Constants.LOCAL_PROTOCOL.equalsIgnoreCase(url.getProtocol())) {
            URL local = URL.valueOf(url.toFullString())
                    .setProtocol(Constants.LOCAL_PROTOCOL)
                    .setHost(LOCALHOST)
                    .setPort(0);
            Class<?> interfaceClass = serviceConfig.getInterfaceClass();
            Object ref = serviceConfig.getRef();
            ServiceClassHolder.getInstance().pushServiceClass(serviceConfig.getServiceClass(ref));
            Exporter<?> exporter = protocol.export(
                    proxyFactory.getInvoker(ref, (Class) interfaceClass, local));
            saveExporter(exporter);
            logger.info("Export dubbo service " + interfaceClass.getName() + " to local registry");
        }
    }

    private void saveExporter(Exporter<?> exporter) {
        List<Exporter<?>> exporterList = exporters.get(serviceConfig);
        if (exporterList == null) {
            exporters.put(serviceConfig, new ArrayList<>());
            exporterList = exporters.get(serviceConfig);
        }
        exporterList.add(exporter);
    }

    /**
     * Register & bind IP address for service provider, can be configured separately.
     * Configuration priority: environment variables -> java system properties -> host property in config file ->
     * /etc/hosts -> default network address -> first available network address
     *
     * @param protocolConfig
     * @param registryURLs
     * @param map
     * @return
     */
    private String findConfigedHosts(ProtocolConfig protocolConfig, List<URL> registryURLs, Map<String, String> map) {
        ProviderConfig provider = serviceConfig.getProvider();
        boolean anyhost = false;

        String hostToBind = getValueFromConfig(protocolConfig, Constants.DUBBO_IP_TO_BIND);
        if (hostToBind != null && hostToBind.length() > 0 && isInvalidLocalHost(hostToBind)) {
            throw new IllegalArgumentException("Specified invalid bind ip from property:" + Constants.DUBBO_IP_TO_BIND + ", value:" + hostToBind);
        }

        // if bind ip is not found in environment, keep looking up
        if (hostToBind == null || hostToBind.length() == 0) {
            hostToBind = protocolConfig.getHost();
            if (provider != null && (hostToBind == null || hostToBind.length() == 0)) {
                hostToBind = provider.getHost();
            }
            if (isInvalidLocalHost(hostToBind)) {
                anyhost = true;
                try {
                    hostToBind = InetAddress.getLocalHost().getHostAddress();
                } catch (UnknownHostException e) {
                    logger.warn(e.getMessage(), e);
                }
                if (isInvalidLocalHost(hostToBind)) {
                    if (registryURLs != null && !registryURLs.isEmpty()) {
                        for (URL registryURL : registryURLs) {
                            if (Constants.MULTICAST.equalsIgnoreCase(registryURL.getParameter("registry"))) {
                                // skip multicast registry since we cannot connect to it via Socket
                                continue;
                            }
                            try {
                                Socket socket = new Socket();
                                try {
                                    SocketAddress addr = new InetSocketAddress(registryURL.getHost(), registryURL.getPort());
                                    socket.connect(addr, 1000);
                                    hostToBind = socket.getLocalAddress().getHostAddress();
                                    break;
                                } finally {
                                    try {
                                        socket.close();
                                    } catch (Throwable e) {
                                    }
                                }
                            } catch (Exception e) {
                                logger.warn(e.getMessage(), e);
                            }
                        }
                    }
                    if (isInvalidLocalHost(hostToBind)) {
                        hostToBind = getLocalHost();
                    }
                }
            }
        }

        map.put(Constants.BIND_IP_KEY, hostToBind);

        // registry ip is not used for bind ip by default
        String hostToRegistry = getValueFromConfig(protocolConfig, Constants.DUBBO_IP_TO_REGISTRY);
        if (hostToRegistry != null && hostToRegistry.length() > 0 && isInvalidLocalHost(hostToRegistry)) {
            throw new IllegalArgumentException("Specified invalid registry ip from property:" + Constants.DUBBO_IP_TO_REGISTRY + ", value:" + hostToRegistry);
        } else if (hostToRegistry == null || hostToRegistry.length() == 0) {
            // bind ip is used as registry ip by default
            hostToRegistry = hostToBind;
        }

        map.put(Constants.ANYHOST_KEY, String.valueOf(anyhost));

        return hostToRegistry;
    }

    /**
     * Register port and bind port for the provider, can be configured separately
     * Configuration priority: environment variable -> java system properties -> port property in protocol config file
     * -> protocol default port
     *
     * @param protocolConfig
     * @return
     */
    private Integer findConfigedPorts(ProtocolConfig protocolConfig, Map<String, String> map) {
        String name = protocolConfig.getName();
        ProviderConfig provider = serviceConfig.getProvider();
        Integer portToBind = null;

        // parse bind port from environment
        String port = getValueFromConfig(protocolConfig, Constants.DUBBO_PORT_TO_BIND);
        portToBind = parsePort(port);

        // if there's no bind port found from environment, keep looking up.
        if (portToBind == null) {
            portToBind = protocolConfig.getPort();
            if (provider != null && (portToBind == null || portToBind == 0)) {
                portToBind = provider.getPort();
            }
            final int defaultPort = ExtensionLoader.getExtensionLoader(Protocol.class).getExtension(name).getDefaultPort();
            if (portToBind == null || portToBind == 0) {
                portToBind = defaultPort;
            }
            if (portToBind == null || portToBind <= 0) {
                portToBind = getRandomPort(name);
                if (portToBind == null || portToBind < 0) {
                    portToBind = getAvailablePort(defaultPort);
                    putRandomPort(name, portToBind);
                }
                logger.warn("Use random available port(" + portToBind + ") for protocol " + name);
            }
        }

        // save bind port, used as url's key later
        map.put(Constants.BIND_PORT_KEY, String.valueOf(portToBind));

        // registry port, not used as bind port by default
        String portToRegistryStr = getValueFromConfig(protocolConfig, Constants.DUBBO_PORT_TO_REGISTRY);
        Integer portToRegistry = parsePort(portToRegistryStr);
        if (portToRegistry == null) {
            portToRegistry = portToBind;
        }

        return portToRegistry;
    }

    private Integer parsePort(String configPort) {
        Integer port = null;
        if (configPort != null && configPort.length() > 0) {
            try {
                Integer intPort = Integer.parseInt(configPort);
                if (isInvalidPort(intPort)) {
                    throw new IllegalArgumentException("Specified invalid port from env value:" + configPort);
                }
                port = intPort;
            } catch (Exception e) {
                throw new IllegalArgumentException("Specified invalid port from env value:" + configPort);
            }
        }
        return port;
    }

    private String getValueFromConfig(ProtocolConfig protocolConfig, String key) {
        String protocolPrefix = protocolConfig.getName().toUpperCase() + "_";
        String port = ConfigUtils.getSystemProperty(protocolPrefix + key);
        if (port == null || port.length() == 0) {
            port = ConfigUtils.getSystemProperty(key);
        }
        return port;
    }

    private static Integer getRandomPort(String protocol) {
        protocol = protocol.toLowerCase();
        if (RANDOM_PORT_MAP.containsKey(protocol)) {
            return RANDOM_PORT_MAP.get(protocol);
        }
        return Integer.MIN_VALUE;
    }

    private static void putRandomPort(String protocol, Integer port) {
        protocol = protocol.toLowerCase();
        if (!RANDOM_PORT_MAP.containsKey(protocol)) {
            RANDOM_PORT_MAP.put(protocol, port);
        }
    }

    private void initConfig(DubboBootstrap bootstrap) {
        if (serviceConfig.getProvider() == null) {
            serviceConfig.setProvider(bootstrap.getProvider() == null ? new ProviderConfig() : bootstrap.getProvider());
        }
        if (serviceConfig.getModule() == null) {
            serviceConfig.setModule(bootstrap.getModule());
        }
        if (serviceConfig.getApplication() == null) {
            serviceConfig.setApplication(bootstrap.getApplication() == null ? new ApplicationConfig() : bootstrap.getApplication());
        }
        if (serviceConfig.getMonitor() == null) {
            serviceConfig.setMonitor(bootstrap.getMonitor() == null ? new MonitorConfig() : bootstrap.getMonitor());
        }
        if (serviceConfig.getProtocols() == null) {
            serviceConfig.setProtocols(bootstrap.getProtocols());
        }
        if (serviceConfig.getRegistries() == null) {
            serviceConfig.setRegistries(bootstrap.getRegistries());
        }

        ProviderConfig provider = serviceConfig.getProvider();
        if (provider != null) {
            if (serviceConfig.getApplication() == null) {
                serviceConfig.setApplication(provider.getApplication());
            }
            if (serviceConfig.getModule() == null) {
                serviceConfig.setModule(provider.getModule());
            }
            if (serviceConfig.getRegistries() == null) {
                serviceConfig.setRegistries(provider.getRegistries());
            }
            if (serviceConfig.getMonitor() == null) {
                serviceConfig.setMonitor(provider.getMonitor());
            }
            if (serviceConfig.getProtocols() == null) {
                serviceConfig.setProtocols(provider.getProtocols());
            }
        }
        ModuleConfig module = serviceConfig.getModule();
        if (module != null) {
            if (serviceConfig.getRegistries() == null) {
                serviceConfig.setRegistries(module.getRegistries());
            }
            if (serviceConfig.getMonitor() == null) {
                serviceConfig.setMonitor(module.getMonitor());
            }
        }
        ApplicationConfig application = serviceConfig.getApplication();
        if (application != null) {
            if (serviceConfig.getRegistries() == null) {
                serviceConfig.setRegistries(application.getRegistries());
            }
            if (serviceConfig.getMonitor() == null) {
                serviceConfig.setMonitor(application.getMonitor());
            }
        }
        serviceConfig.checkApplication();
        serviceConfig.checkRegistry();
        serviceConfig.checkProtocol();
        serviceConfig.checkStubAndMock(serviceConfig.getInterfaceClass());
    }

    public void unexport() {
        exporters.keySet().forEach(this::unexport);
        exporters.clear();
    }

    public void unexport(ServiceConfig config) {
        List<Exporter<?>> exporterList = exporters.remove(config);
        exporterList.forEach(Exporter::unexport);
    }

}
