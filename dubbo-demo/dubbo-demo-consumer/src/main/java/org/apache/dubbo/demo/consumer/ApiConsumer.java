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
package org.apache.dubbo.demo.consumer;

import org.apache.dubbo.bootstrap.DubboBootstrap;
import org.apache.dubbo.bootstrap.ReferenceConfigBuilder;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ConsumerConfig;
import org.apache.dubbo.config.RegistryConfig;

/**
 *
 */
public class ApiConsumer {

    public static void main(String[] args) throws Exception {
        ApplicationConfig applicationConfig = new ApplicationConfig();
        applicationConfig.setName("test-api-consumer");

        RegistryConfig registryConfig = new RegistryConfig();
        registryConfig.setAddress("zookeeper://127.0.0.1:2181");

        ConsumerConfig consumerConfig = new ConsumerConfig();
        consumerConfig.setTimeout(5000);

        DubboBootstrap bootstrap = DubboBootstrap.getInstance()
                .applicationConfig(applicationConfig)
                .registryConfig(registryConfig)
                .consumerConfig(consumerConfig);

        bootstrap.refer(
                ReferenceConfigBuilder.create()
                        .interfaceName("org.apache.dubbo.demo.DemoService")
        );

        System.in.read();
/*
        // otherwise
        bootstrap.export(
                ServiceConfigBuilder.create()
                .interfaceName("org.apache.dubbo.demo.DemoService")
                .ref(demoService)
                .timeout(5000)
        );
*/
    }

}
