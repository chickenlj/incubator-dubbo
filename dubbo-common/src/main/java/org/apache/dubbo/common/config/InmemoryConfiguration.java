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
package org.apache.dubbo.common.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 *
 */
public class InmemoryConfiguration extends AbstractConfiguration {

    /**
     * stores the configuration key-value pairs
     */
    private Map<String, String> store = new LinkedHashMap<>();

    public InmemoryConfiguration() {
    }

    /**
     * Replace previous value if there is one
     *
     * @param key
     * @param value
     */
    public void addProperty(String key, String value) {
        store.put(key, value);
    }

    public void addPropertys(Map<String, String> properties) {
        store.putAll(properties);
    }

    @Override
    public String getString(String key) {
        return (String) store.get(key);
    }

    @Override
    public String getString(String key, String defaultValue) {
        if (store.containsKey(key)) {
            return store.get(key);
        }
        return defaultValue;
    }

    /*@Override
    public Object getProperty(String key, Object defaultValue) {
        Object value = getInternalProperty(key);
        if (value == null && prefix != null) {
            if (id != null) {
                value = getInternalProperty(prefix + id + "." + key);
            }
            if (value == null) {
                value = getInternalProperty(prefix + key);
            }
        }
        return value;
    }*/

    @Override
    public boolean containsKey(String key) {
        return store.containsKey(key) && store.get(key) != null;
    }

    @Override
    protected Object getInternalProperty(String key) {
        return store.get(key);
    }

}
