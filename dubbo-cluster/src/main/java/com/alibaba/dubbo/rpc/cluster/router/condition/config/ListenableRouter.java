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
import com.alibaba.dubbo.common.utils.CollectionUtils;
import com.alibaba.dubbo.common.utils.StringUtils;
import com.alibaba.dubbo.configcenter.ConfigChangeType;
import com.alibaba.dubbo.configcenter.ConfigChangedEvent;
import com.alibaba.dubbo.configcenter.ConfigurationListener;
import com.alibaba.dubbo.configcenter.DynamicConfiguration;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.cluster.Router;
import com.alibaba.dubbo.rpc.cluster.router.AbstractRouter;
import com.alibaba.dubbo.rpc.cluster.router.condition.ConditionRouter;
import com.alibaba.dubbo.rpc.cluster.router.condition.config.model.ConditionRouterRule;
import com.alibaba.dubbo.rpc.cluster.router.condition.config.model.ConditionRuleParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Abstract router which listens to dynamic configuration
 */
public abstract class ListenableRouter extends AbstractRouter implements ConfigurationListener {
    public static final String NAME = "LISTENABLE_ROUTER";
    public static final String RULE_SUFFIX = ".condition-router";

    private static final Logger logger = LoggerFactory.getLogger(ListenableRouter.class);
    private volatile ConditionRouterRule routerRule;
    private volatile List<ConditionRouter> conditionRouters = Collections.emptyList();
    private final String ruleKey;

    public ListenableRouter(URL url, String ruleKey) {
        super(url);
        this.setForce(false);
        this.init(ruleKey);
        this.ruleKey = ruleKey;
    }

    @Override
    public synchronized void process(ConfigChangedEvent event) {
        if (logger.isDebugEnabled()) {
            logger.debug("Notification of condition rule, change type is: " + event.getChangeType() +
                    ", raw rule is:\n " + event.getContent());
        }

        if (event.getChangeType().equals(ConfigChangeType.DELETED)) {
            routerRule = null;
            conditionRouters = Collections.emptyList();
        } else {
            try {
                routerRule = ConditionRuleParser.parse(event.getContent());
                generateConditions(routerRule);
            } catch (Exception e) {
                logger.error("Failed to parse the raw condition rule. Failed to parse the raw condition rule and it will not take effect, please check " +
                        "if the condition rule matches with the template, the raw rule is:\n " + event.getContent(), e);
            }
        }
    }

    @Override
    public <T> List<Invoker<T>> route(List<Invoker<T>> invokers, URL url, Invocation invocation) throws RpcException {
        if (CollectionUtils.isEmpty(invokers) || conditionRouters.size() == 0) {
            return invokers;
        }

        // We will check enabled status inside each router.
        for (Router router : conditionRouters) {
            invokers = router.route(invokers, url, invocation);
        }

        return invokers;
    }

    @Override
    public boolean isForce() {
        return (routerRule != null && routerRule.isForce());
    }

    private boolean isRuleRuntime() {
        return routerRule != null && routerRule.isValid() && routerRule.isRuntime();
    }

    private void generateConditions(ConditionRouterRule rule) {
        if (rule != null && rule.isValid()) {
            List<ConditionRouter> conditionStateRouters = new ArrayList<ConditionRouter>();
            for (String con : rule.getConditions()) {
                conditionStateRouters.add(new ConditionRouter(this.url, con, rule.isForce(), rule.isEnabled()));
            }
            this.conditionRouters = conditionStateRouters;
        }
    }

    private synchronized void init(String ruleKey) {
        if (StringUtils.isEmpty(ruleKey)) {
            return;
        }
        String routerKey = ruleKey + RULE_SUFFIX;
        this.getRuleRepository().addListener(routerKey, this);
        String rule = this.getRuleRepository().getConfig(routerKey, DynamicConfiguration.DEFAULT_GROUP);
        if (StringUtils.isNotEmpty(rule)) {
            this.process(new ConfigChangedEvent(routerKey, DynamicConfiguration.DEFAULT_GROUP, rule));
        }
    }

//    @Override
//    public void stop() {
//        this.getRuleRepository().removeListener(ruleKey + RULE_SUFFIX, this);
//    }
}
