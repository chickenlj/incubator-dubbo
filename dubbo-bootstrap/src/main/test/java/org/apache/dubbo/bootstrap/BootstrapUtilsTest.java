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

import junit.framework.TestCase;
import org.apache.dubbo.bootstrap.config.InterfaceConfig;
import org.apache.dubbo.common.Constants;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.utils.ConfigUtils;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.MonitorConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.monitor.MonitorService;
import org.apache.dubbo.registry.RegistryService;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class BootstrapUtilsTest {
    @ClassRule
    public static TemporaryFolder tempDir = new TemporaryFolder();
    private static File dubboProperties;


    @BeforeClass
    public static void setUp() throws Exception {
        dubboProperties = tempDir.newFile(Constants.DUBBO_PROPERTIES_KEY);
        System.setProperty(Constants.DUBBO_PROPERTIES_KEY, dubboProperties.getAbsolutePath());
    }

    @AfterClass
    public static void tearDown() throws Exception {
        System.clearProperty(Constants.DUBBO_PROPERTIES_KEY);
    }

    @Test
    public void testLoadRegistries() throws Exception {
        System.setProperty("dubbo.registry.address", "addr1");
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        init(interfaceConfig);
        List<URL> urls = BootstrapUtils.loadRegistries(interfaceConfig, true);
        TestCase.assertEquals(1, urls.size());
        URL url = urls.get(0);
        TestCase.assertEquals("registry", url.getProtocol());
        TestCase.assertEquals("addr1:9090", url.getAddress());
        TestCase.assertEquals(RegistryService.class.getName(), url.getPath());
        TestCase.assertTrue(url.getParameters().containsKey("timestamp"));
        TestCase.assertTrue(url.getParameters().containsKey("pid"));
        TestCase.assertTrue(url.getParameters().containsKey("registry"));
        TestCase.assertTrue(url.getParameters().containsKey("dubbo"));
    }

    @Test
    public void testLoadMonitor() throws Exception {
        System.setProperty("dubbo.monitor.address", "monitor-addr:12080");
        System.setProperty("dubbo.monitor.protocol", "monitor");
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        init(interfaceConfig);
        URL url = BootstrapUtils.loadMonitor(interfaceConfig, new URL("dubbo", "addr1", 9090));
        TestCase.assertEquals("monitor-addr:12080", url.getAddress());
        TestCase.assertEquals(MonitorService.class.getName(), url.getParameter("interface"));
        TestCase.assertNotNull(url.getParameter("dubbo"));
        TestCase.assertNotNull(url.getParameter("pid"));
        TestCase.assertNotNull(url.getParameter("timestamp"));
    }

    private void init(InterfaceConfig interfaceConfig) {
        interfaceConfig.setRegistries(new ArrayList<>());
        RegistryConfig registryConfig = new RegistryConfig();
        registryConfig.setAddress("zookeeper://127.0.0.1:2181");
        interfaceConfig.getRegistries().add(registryConfig);

        interfaceConfig.setApplication(new ApplicationConfig());
        interfaceConfig.setMonitor(new MonitorConfig());
    }

    @Test
    public void testCheckRegistry1() throws Exception {
        System.setProperty("dubbo.registry.address", "addr1|addr2");
        try {
            InterfaceConfig interfaceConfig = new InterfaceConfig();
            interfaceConfig.checkRegistry();
            TestCase.assertEquals(2, interfaceConfig.getRegistries().size());
        } finally {
            System.clearProperty("dubbo.registry.address");
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testCheckRegistry2() throws Exception {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.checkRegistry();
    }

    @Test
    public void checkApplication1() throws Exception {
        try {
            ConfigUtils.setProperties(null);
            System.clearProperty(Constants.SHUTDOWN_WAIT_KEY);
            System.clearProperty(Constants.SHUTDOWN_WAIT_SECONDS_KEY);

//            writeDubboProperties(Constants.SHUTDOWN_WAIT_KEY, "100");
            System.setProperty("dubbo.application.name", "demo");
            InterfaceConfig interfaceConfig = new InterfaceConfig();
            interfaceConfig.setApplication(new ApplicationConfig());
            interfaceConfig.checkApplication();
            ApplicationConfig appConfig = interfaceConfig.getApplication();
            TestCase.assertEquals("demo", BootstrapUtils.getCompositeProperty(appConfig, "name", null));
//            TestCase.assertEquals("100", System.getProperty(Constants.SHUTDOWN_WAIT_KEY));

            System.clearProperty(Constants.SHUTDOWN_WAIT_KEY);
            ConfigUtils.setProperties(null);
//            writeDubboProperties(Constants.SHUTDOWN_WAIT_SECONDS_KEY, "1000");
            System.setProperty("dubbo.application.name", "demo");
            interfaceConfig = new InterfaceConfig();
            interfaceConfig.setApplication(new ApplicationConfig());
            interfaceConfig.checkApplication();
//            TestCase.assertEquals("1000", System.getProperty(Constants.SHUTDOWN_WAIT_SECONDS_KEY));
        } finally {
            ConfigUtils.setProperties(null);
            System.clearProperty("dubbo.application.name");
            System.clearProperty(Constants.SHUTDOWN_WAIT_KEY);
            System.clearProperty(Constants.SHUTDOWN_WAIT_SECONDS_KEY);
        }
    }

    @Test(expected = IllegalStateException.class)
    public void checkApplication2() throws Exception {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.checkApplication();
    }


    private void writeDubboProperties(String key, String value) {
        OutputStream os = null;
        try {
            os = new BufferedOutputStream(new FileOutputStream(dubboProperties));
            Properties properties = new Properties();
            properties.put(key, value);
            properties.store(os, "");
            os.close();
        } catch (IOException e) {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException ioe) {
                    // ignore
                }
            }
        }
    }

}
