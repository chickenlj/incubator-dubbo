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
import org.apache.dubbo.common.config.Environment;

/**
 *
 */
public abstract class AbstractDynamicConfiguration extends AbstractConfiguration implements DynamicConfiguration {
    protected URL url;

    protected Environment env;

    public AbstractDynamicConfiguration() {
        env = Environment.getInstance();
    }

    @Override
    public void addListener(String key, ConfigurationListener listener) {

    }

    @Override
    public Object getProperty(String key, Object defaultValue) {
        return getInternalProperty(key);
    }

    @Override
    public String getConfig(String key, String group) {
        return null;
    }

    @Override
    public String getConfig(String key, String group, ConfigurationListener listener) {
        return null;
    }

    @Override
    public String getConfig(String key, String group, long timeout) {
        return null;
    }

    @Override
    public boolean containsKey(String key) {
        return getProperty(key) != null;
    }

    public URL getUrl() {
        return url;
    }

    public void setUrl(URL url) {
        this.url = url;
    }

    protected abstract String getInternalProperty(String key, String group, long timeout);

}
