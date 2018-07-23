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

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.config.AbstractConfiguration;

/**
 *
 */
public abstract class AbstractDynamicConfiguration extends AbstractConfiguration implements DynamicConfiguration {

    protected String env;
    protected String address;
    protected String group;
    protected String namespace;
    protected String cluster;
    protected String app;

    @Override
    public void addListener(URL url, ConfigurationListener listener) {

    }

    @Override
    public Object getProperty(String key, Object defaultValue) {
        return getInternalProperty(key);
    }

    @Override
    public String getProperty(String key, String group) {
        return null;
    }

    @Override
    public String getProperty(String key, String group, long timeout) {
        return null;
    }

    @Override
    public boolean containsKey(String key) {
        return getProperty(key) != null;
    }

    protected abstract String getInternalProperty(String key, String group, long timeout);

    public String getEnv() {
        return env;
    }

    public void setEnv(String env) {
        this.env = env;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getCluster() {
        return cluster;
    }

    public void setCluster(String cluster) {
        this.cluster = cluster;
    }

    public String getApp() {
        return app;
    }

    public void setApp(String app) {
        this.app = app;
    }
}
