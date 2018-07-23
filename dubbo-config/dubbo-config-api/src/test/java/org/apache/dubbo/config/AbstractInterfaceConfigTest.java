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
package org.apache.dubbo.config;

import junit.framework.TestCase;
import org.apache.dubbo.common.Constants;
import org.apache.dubbo.config.api.Greeting;
import org.apache.dubbo.config.mock.GreetingLocal1;
import org.apache.dubbo.config.mock.GreetingLocal2;
import org.apache.dubbo.config.mock.GreetingLocal3;
import org.apache.dubbo.config.mock.GreetingMock1;
import org.apache.dubbo.config.mock.GreetingMock2;
import org.apache.dubbo.config.model.InterfaceConfig;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Collections;

public class AbstractInterfaceConfigTest {
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

    @Test(expected = IllegalStateException.class)
    public void checkInterfaceAndMethods1() throws Exception {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.checkInterfaceAndMethods(null, null);
    }

    @Test(expected = IllegalStateException.class)
    public void checkInterfaceAndMethods2() throws Exception {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.checkInterfaceAndMethods(AbstractInterfaceConfigTest.class, null);
    }

    @Test(expected = IllegalStateException.class)
    public void checkInterfaceAndMethod3() throws Exception {
        MethodConfig methodConfig = new MethodConfig();
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.checkInterfaceAndMethods(Greeting.class, Collections.singletonList(methodConfig));
    }

    @Test(expected = IllegalStateException.class)
    public void checkInterfaceAndMethod4() throws Exception {
        MethodConfig methodConfig = new MethodConfig();
        methodConfig.setName("nihao");
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.checkInterfaceAndMethods(Greeting.class, Collections.singletonList(methodConfig));
    }

    @Test
    public void checkInterfaceAndMethod5() throws Exception {
        MethodConfig methodConfig = new MethodConfig();
        methodConfig.setName("hello");
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.checkInterfaceAndMethods(Greeting.class, Collections.singletonList(methodConfig));
    }

    @Test(expected = IllegalStateException.class)
    public void checkStubAndMock1() throws Exception {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.setLocal(GreetingLocal1.class.getName());
        interfaceConfig.checkStubAndMock(Greeting.class);
    }

    @Test(expected = IllegalStateException.class)
    public void checkStubAndMock2() throws Exception {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.setLocal(GreetingLocal2.class.getName());
        interfaceConfig.checkStubAndMock(Greeting.class);
    }

    @Test
    public void checkStubAndMock3() throws Exception {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.setLocal(GreetingLocal3.class.getName());
        interfaceConfig.checkStubAndMock(Greeting.class);
    }

    @Test(expected = IllegalStateException.class)
    public void checkStubAndMock4() throws Exception {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.setStub(GreetingLocal1.class.getName());
        interfaceConfig.checkStubAndMock(Greeting.class);
    }

    @Test(expected = IllegalStateException.class)
    public void checkStubAndMock5() throws Exception {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.setStub(GreetingLocal2.class.getName());
        interfaceConfig.checkStubAndMock(Greeting.class);
    }

    @Test
    public void checkStubAndMock6() throws Exception {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.setStub(GreetingLocal3.class.getName());
        interfaceConfig.checkStubAndMock(Greeting.class);
    }

    @Test(expected = IllegalStateException.class)
    public void checkStubAndMock7() throws Exception {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.setMock("return {a, b}");
        interfaceConfig.checkStubAndMock(Greeting.class);
    }

    @Test(expected = IllegalStateException.class)
    public void checkStubAndMock8() throws Exception {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.setMock(GreetingMock1.class.getName());
        interfaceConfig.checkStubAndMock(Greeting.class);
    }

    @Test(expected = IllegalStateException.class)
    public void checkStubAndMock9() throws Exception {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.setMock(GreetingMock2.class.getName());
        interfaceConfig.checkStubAndMock(Greeting.class);
    }

    @Test
    public void testLocal() throws Exception {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.setLocal((Boolean) null);
        TestCase.assertNull(interfaceConfig.getLocal());
        interfaceConfig.setLocal(true);
        TestCase.assertEquals("true", interfaceConfig.getLocal());
        interfaceConfig.setLocal("GreetingMock");
        TestCase.assertEquals("GreetingMock", interfaceConfig.getLocal());
    }

    @Test
    public void testStub() throws Exception {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.setStub((Boolean) null);
        TestCase.assertNull(interfaceConfig.getStub());
        interfaceConfig.setStub(true);
        TestCase.assertEquals("true", interfaceConfig.getStub());
        interfaceConfig.setStub("GreetingMock");
        TestCase.assertEquals("GreetingMock", interfaceConfig.getStub());
    }

    @Test
    public void testCluster() throws Exception {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.setCluster("mockcluster");
        TestCase.assertEquals("mockcluster", interfaceConfig.getCluster());
    }

    @Test
    public void testProxy() throws Exception {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.setProxy("mockproxyfactory");
        TestCase.assertEquals("mockproxyfactory", interfaceConfig.getProxy());
    }

    @Test
    public void testConnections() throws Exception {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.setConnections(1);
        TestCase.assertEquals(1, interfaceConfig.getConnections().intValue());
    }

    @Test
    public void testFilter() throws Exception {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.setFilter("mockfilter");
        TestCase.assertEquals("mockfilter", interfaceConfig.getFilter());
    }

    @Test
    public void testListener() throws Exception {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.setListener("mockinvokerlistener");
        TestCase.assertEquals("mockinvokerlistener", interfaceConfig.getListener());
    }

    @Test
    public void testLayer() throws Exception {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.setLayer("layer");
        TestCase.assertEquals("layer", interfaceConfig.getLayer());
    }

    @Test
    public void testApplication() throws Exception {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        ApplicationConfig applicationConfig = new ApplicationConfig();
        interfaceConfig.setApplication(applicationConfig);
        TestCase.assertSame(applicationConfig, interfaceConfig.getApplication());
    }

    @Test
    public void testModule() throws Exception {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        ModuleConfig moduleConfig = new ModuleConfig();
        interfaceConfig.setModule(moduleConfig);
        TestCase.assertSame(moduleConfig, interfaceConfig.getModule());
    }

    @Test
    public void testRegistry() throws Exception {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        RegistryConfig registryConfig = new RegistryConfig();
        interfaceConfig.setRegistry(registryConfig);
        TestCase.assertSame(registryConfig, interfaceConfig.getRegistry());
    }

    @Test
    public void testRegistries() throws Exception {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        RegistryConfig registryConfig = new RegistryConfig();
        interfaceConfig.setRegistries(Collections.singletonList(registryConfig));
        TestCase.assertEquals(1, interfaceConfig.getRegistries().size());
        TestCase.assertSame(registryConfig, interfaceConfig.getRegistries().get(0));
    }

    @Test
    public void testMonitor() throws Exception {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.setMonitor("monitor-addr");
        TestCase.assertEquals("monitor-addr", interfaceConfig.getMonitor().getAddress());
        MonitorConfig monitorConfig = new MonitorConfig();
        interfaceConfig.setMonitor(monitorConfig);
        TestCase.assertSame(monitorConfig, interfaceConfig.getMonitor());
    }

    @Test
    public void testOwner() throws Exception {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.setOwner("owner");
        TestCase.assertEquals("owner", interfaceConfig.getOwner());
    }

    @Test
    public void testCallbacks() throws Exception {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.setCallbacks(2);
        TestCase.assertEquals(2, interfaceConfig.getCallbacks().intValue());
    }

    @Test
    public void testOnconnect() throws Exception {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.setOnconnect("onConnect");
        TestCase.assertEquals("onConnect", interfaceConfig.getOnconnect());
    }

    @Test
    public void testOndisconnect() throws Exception {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.setOndisconnect("onDisconnect");
        TestCase.assertEquals("onDisconnect", interfaceConfig.getOndisconnect());
    }

    @Test
    public void testScope() throws Exception {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.setScope("scope");
        TestCase.assertEquals("scope", interfaceConfig.getScope());
    }
}
