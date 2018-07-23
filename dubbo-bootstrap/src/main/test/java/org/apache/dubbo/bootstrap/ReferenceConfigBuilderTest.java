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

import org.apache.dubbo.common.Constants;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.test.config.api.DemoService;
import org.apache.dubbo.test.config.impl.DemoServiceImpl;
import org.junit.Assert;
import org.junit.Test;

public class ReferenceConfigBuilderTest {

    @Test
    public void testInjvm() throws Exception {
        ApplicationConfig application = new ApplicationConfig();
        application.setName("test-protocol-random-port");

        RegistryConfig registry = new RegistryConfig();
        registry.setAddress("multicast://224.5.6.7:1234");

        ProtocolConfig protocol = new ProtocolConfig();
        protocol.setName("dubbo");

        ServiceConfigBuilder<DemoService> demoService;
        demoService = new ServiceConfigBuilder<DemoService>();
        demoService.setInterface(DemoService.class);
        demoService.setRef(new DemoServiceImpl());
        demoService.setApplication(application);
        demoService.setRegistry(registry);
        demoService.setProtocol(protocol);

        ReferenceConfigBuilder<DemoService> rc = new ReferenceConfigBuilder<DemoService>();
        rc.setApplication(application);
        rc.setRegistry(registry);
        rc.setInterface(DemoService.class.getName());
        rc.setInjvm(false);

        try {
            demoService.export();
            rc.get();
            Assert.assertTrue(!Constants.LOCAL_PROTOCOL.equalsIgnoreCase(
                    rc.getInvoker().getUrl().getProtocol()));
        } finally {
            demoService.unexport();
        }
    }

}