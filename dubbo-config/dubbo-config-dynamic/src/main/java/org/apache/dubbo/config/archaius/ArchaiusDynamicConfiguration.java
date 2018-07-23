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
import org.apache.dubbo.common.URL;
import org.apache.dubbo.config.AbstractDynamicConfiguration;
import org.apache.dubbo.config.ConfigurationListener;
import org.apache.dubbo.config.archaius.sources.ZooKeeperConfigurationSource;

/**
 * Archaius supports various sources and it's extensiable: JDBC, ZK, Properties, ..., so should we make it extensiable?
 */
public class ArchaiusDynamicConfiguration extends AbstractDynamicConfiguration {

    public ArchaiusDynamicConfiguration() {
        if (address != null) {
            System.setProperty(ZooKeeperConfigurationSource.ARCHAIUS_SOURCE_ADDRESS_KEY, address);
        }
        if (app != null) {
            System.setProperty(ZooKeeperConfigurationSource.ARCHAIUS_CONFIG_ROOT_PATH_KEY, ZooKeeperConfigurationSource.DEFAULT_CONFIG_ROOT_PATH + "/" + app);
        }

        try {
            ZooKeeperConfigurationSource zkConfigSource = new ZooKeeperConfigurationSource();
            zkConfigSource.start();
            DynamicWatchedConfiguration zkDynamicConfig = new DynamicWatchedConfiguration(zkConfigSource);
            ConfigurationManager.install(zkDynamicConfig);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void addListener(URL url, ConfigurationListener listener) {

    }

    @Override
    public URL instrument(URL url) {
        return null;
    }

    @Override
    protected String getInternalProperty(String key, String group, long timeout) {
        return DynamicPropertyFactory.getInstance()
                .getStringProperty(key, null)
                .get();
    }

    @Override
    protected Object getInternalProperty(String key) {
        return getInternalProperty(key, null, 0L);
    }

    private class ArchaiusListener {

    }
}
