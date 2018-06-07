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
package com.alibaba.dubbo.common.config;

/**
 * This is an abstraction specially customized for the sequence Dubbo retrieves properties.
 */
public abstract class AbstractPrefixConfiguration extends AbstractConfiguration {

    private String id;
    private String prefix;

    public AbstractPrefixConfiguration(String prefix, String id) {
        super();
        this.prefix = prefix;
        this.id = id;
    }

    @Override
    public final Object getProperty(String key, Object defaultValue) {
        Object value = getInternalProperty(key);
        if (value == null && id != null) {
            value = getInternalProperty(prefix + id + "." + key);
            if (value == null) {
                value = getInternalProperty(prefix + key);
            }
        }
        return value;
    }
}
