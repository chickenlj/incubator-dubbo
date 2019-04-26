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
package org.apache.dubbo.config.builders;

import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.config.context.ConfigManager;
import org.apache.dubbo.config.utils.ReferenceConfigCache;
import org.apache.dubbo.configcenter.ConfigChangeEvent;
import org.apache.dubbo.configcenter.ConfigurationListener;
import org.apache.dubbo.rpc.model.ApplicationModel;

import java.util.concurrent.atomic.AtomicBoolean;

public class DubboServer {

    private ApplicationModel applicationModel; //要实例化

    private ServerStatus status;
    private AtomicBoolean started;

    private ConfigManager configManager = new ConfigManager();

    List<Protocol> protocols;

    void addProtocol(ProtocolConfig protocolConfig) {
        configManager.
    }

    void addApplication(ApplicationConfig applicationConfig) {

    }

    void addService(ServiceConfig serviceConfig) {
        serviceConfig.setServer(this);
        configManager.addService(serviceConfig);
    }

    void export(ServiceConfig sc) {

    }

    T refer(ReferenceConfig rc) {

    }

    void start() {
        // 先组织元数据
        for (ServiceConfig sc : services) {
            sc.export(applicationModel);
        }
        // 再开端口、发布地址
        for (ProtocolConfig p : protocols) {
            p.startProtocolServer();
        }

        application.

        for (ReferenceConfig rc : references) {
            ReferenceConfigCache.refer(rc);
        }
    }

    class GovernanceRule implements ConfigurationListener {

        @Override
        public void process(ConfigChangeEvent event) {

        }

    }
}
