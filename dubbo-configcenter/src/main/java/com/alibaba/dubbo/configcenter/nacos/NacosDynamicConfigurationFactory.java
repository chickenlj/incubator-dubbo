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

package com.alibaba.dubbo.configcenter.nacos;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.configcenter.AbstractDynamicConfigurationFactory;
import com.alibaba.dubbo.configcenter.DynamicConfiguration;

import static com.alibaba.dubbo.common.Constants.CONFIG_NAMESPACE_KEY;

/**
 * The nacos implementation of {@link AbstractDynamicConfigurationFactory}
 */
public class NacosDynamicConfigurationFactory extends AbstractDynamicConfigurationFactory {

    @Override
    protected DynamicConfiguration createDynamicConfiguration(URL url) {
        URL nacosURL = url;
        if ("dubbo".equals(url.getParameter(CONFIG_NAMESPACE_KEY))) {
            // Nacos use empty string as default name space, replace default namespace "dubbo" to ""
            nacosURL = url.removeParameter(CONFIG_NAMESPACE_KEY);
        }
        return new NacosDynamicConfiguration(nacosURL);
    }
}
