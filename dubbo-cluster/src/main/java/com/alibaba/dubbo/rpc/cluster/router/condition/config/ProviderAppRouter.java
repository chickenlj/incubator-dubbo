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
package com.alibaba.dubbo.rpc.cluster.router.condition.config;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.common.utils.StringUtils;
import com.alibaba.dubbo.configcenter.CollectionUtils;
import com.alibaba.dubbo.configcenter.ConfigChangedEvent;
import com.alibaba.dubbo.configcenter.DynamicConfiguration;
import com.alibaba.dubbo.rpc.Invoker;

import java.util.List;

import static com.alibaba.dubbo.common.utils.StringUtils.isEmpty;

/**
 * Application level router, "application.condition-router"
 */
public class ProviderAppRouter extends ListenableRouter {
    private static final Logger logger = LoggerFactory.getLogger(ListenableRouter.class);
    public static final String NAME = "PROVIDER_APP_ROUTER";
    private String application;
    private final String currentApplication;

    public ProviderAppRouter(URL url) {
        super(url, url.getApplication());
        this.currentApplication = url.getApplication();
    }

    @Override
    public <T> void notify(List<Invoker<T>> invokers) {
        if (CollectionUtils.isEmpty(invokers)) {
            return;
        }

        Invoker<T> invoker = invokers.get(0);
        URL url = invoker.getUrl();
        String providerApplication = url.getRemoteApplication();

        // provider application is empty or equals with the current application
        if (isEmpty(providerApplication) || providerApplication.equals(currentApplication)) {
            logger.warn("condition router get providerApplication is empty, will not subscribe to provider app rules.");
            return;
        }

        synchronized (this) {
            if (!providerApplication.equals(application)) {
                if (StringUtils.isNotEmpty(application)) {
                    this.getRuleRepository().removeListener(application + RULE_SUFFIX, this);
                }
                String key = providerApplication + RULE_SUFFIX;
                this.getRuleRepository().addListener(key, this);
                application = providerApplication;
                String rawRule = this.getRuleRepository().getConfig(key, DynamicConfiguration.DEFAULT_GROUP);
                if (StringUtils.isNotEmpty(rawRule)) {
                    this.process(new ConfigChangedEvent(key, DynamicConfiguration.DEFAULT_GROUP, rawRule));
                }
            }
        }
    }
}
