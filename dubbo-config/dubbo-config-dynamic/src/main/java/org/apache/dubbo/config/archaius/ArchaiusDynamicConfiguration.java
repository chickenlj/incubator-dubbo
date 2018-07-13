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
package org.apache.dubbo.config.archaius;

import com.netflix.config.ConfigurationManager;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.config.DynamicWatchedConfiguration;
import com.netflix.config.source.ZooKeeperConfigurationSource;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.dubbo.config.AbstractDynamicConfiguration;
import org.apache.dubbo.config.ConfigurationListener;

/**
 *
 */
public class ArchaiusDynamicConfiguration extends AbstractDynamicConfiguration {

    /**
     * Ensure that this extension get loaded during app start.
     */
    public ArchaiusDynamicConfiguration() {
        String zkConfigRootPath = "/dubbo/config/archaius";

        CuratorFramework client = CuratorFrameworkFactory.newClient("127.0.0.1:2181", 60 * 1000, 60 * 1000,
                new ExponentialBackoffRetry(1000, 3));

        ZooKeeperConfigurationSource zkConfigSource = new ZooKeeperConfigurationSource(client, zkConfigRootPath);
        try {
            client.start();
            zkConfigSource.start();
        } catch (Exception e) {
            e.printStackTrace();
        }

        DynamicWatchedConfiguration zkDynamicConfig = new DynamicWatchedConfiguration(zkConfigSource);

        ConfigurationManager.install(zkDynamicConfig);
    }

    @Override
    public void addListener(ConfigurationListener listener) {

    }

    @Override
    protected Object getInternalProperty(String key) {
        return DynamicPropertyFactory.getInstance()
                .getStringProperty(key, null)
                .get();
    }
}
