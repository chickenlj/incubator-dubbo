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
package com.alibaba.dubbo.configcenter;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.extension.ExtensionLoader;

public class DynamicConfigurationInstance {

    private static volatile DynamicConfiguration dynamicConfiguration;

    public static synchronized void init(URL url) {
        if (url == null) {
            url = URL.valueOf("nop://");
        }

        String protocol = url.getProtocol();
        ExtensionLoader<DynamicConfigurationFactory> loader = ExtensionLoader.getExtensionLoader(DynamicConfigurationFactory.class);
        DynamicConfigurationFactory factory = loader.getExtension(protocol);
        dynamicConfiguration = factory.getDynamicConfiguration(url);
    }

    public static DynamicConfiguration getInstance() {
        return dynamicConfiguration;
    }

}
