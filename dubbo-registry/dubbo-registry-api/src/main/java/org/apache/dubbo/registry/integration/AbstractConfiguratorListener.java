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
package org.apache.dubbo.registry.integration;

import org.apache.dubbo.common.config.configcenter.ConfigChangeType;
import org.apache.dubbo.common.config.configcenter.ConfigChangedEvent;
import org.apache.dubbo.common.config.configcenter.ConfigurationListener;
import org.apache.dubbo.common.config.configcenter.DynamicConfiguration;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.rpc.cluster.Configurator;
import org.apache.dubbo.rpc.cluster.configurator.parser.ConfigParser;
import org.apache.dubbo.rpc.cluster.configurator.parser.model.ConfiguratorConfig;
import org.apache.dubbo.rpc.cluster.governance.GovernanceRuleRepository;
import org.apache.dubbo.rpc.model.ApplicationModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.apache.dubbo.registry.Constants.CONFIGURATORS_SUFFIX;
import static org.apache.dubbo.rpc.Constants.SCOPE_KEY;

/**
 * AbstractConfiguratorListener
 */
public abstract class AbstractConfiguratorListener implements ConfigurationListener {
    private static final Logger logger = LoggerFactory.getLogger(AbstractConfiguratorListener.class);

    protected List<Configurator> configurators = Collections.emptyList();
    protected GovernanceRuleRepository ruleRepository = ExtensionLoader.getExtensionLoader(
            GovernanceRuleRepository.class).getDefaultExtension();
    private String key;

    protected final void initWith(String key) {
        this.key = key;
        ruleRepository.addListener(key, this);
        String rawConfig = ruleRepository.getRule(key, DynamicConfiguration.DEFAULT_GROUP);
        if (!StringUtils.isEmpty(rawConfig)) {
            genConfiguratorsFromRawRule(rawConfig, key);
        }
    }

    public void stopListen() {
        ruleRepository.removeListener(key, this);
    }

    @Override
    public void process(ConfigChangedEvent event) {
        if (logger.isInfoEnabled()) {
            logger.info("Notification of overriding rule, change type is: " + event.getChangeType() +
                    ", raw config content is:\n " + event.getContent());
        }

        if (event.getChangeType().equals(ConfigChangeType.DELETED)) {
            configurators.clear();
        } else {
            if (!genConfiguratorsFromRawRule(event.getContent(), event.getKey())) {
                return;
            }
        }

        notifyOverrides();
    }

    private boolean genConfiguratorsFromRawRule(String rawConfig, String key) {
        boolean parseSuccess = true;
        try {
            // parseConfigurators will recognize app/service config automatically.
            configurators = Configurator.toConfigurators(ConfigParser.parseConfigurators(rawConfig))
                    .orElse(configurators);

            // remove invalid configurators
            removeConfiguratorsIfNotMatched(key);
        } catch (Exception e) {
            logger.error("Failed to parse raw dynamic config and it will not take effect, the raw config is: " +
                    rawConfig, e);
            parseSuccess = false;
        }
        return parseSuccess;
    }

    protected void removeConfiguratorsIfNotMatched(String key) {
        if (StringUtils.isNotEmpty(key)) {
            int index = key.lastIndexOf(CONFIGURATORS_SUFFIX);
            if (index >= 0 && this.configurators != null) {
                List<Configurator> filtered = new ArrayList<>();
                for (Configurator configurator : configurators) {
                    if (ConfiguratorConfig.SCOPE_SERVICE
                            .equals(configurator.getUrl().getParameter(SCOPE_KEY))) {
                        String ruleKey = DynamicConfiguration.getRuleKey(configurator.getUrl()) + CONFIGURATORS_SUFFIX;
                        if (key.equals(ruleKey)) {
                            /**
                             * Matches the key of the interface rule.
                             * The interface information that does not match is filtered out.
                             *
                             * Interface rules can take effect for specified app names.
                             * The app name is not configured or matches the app name.
                             */
                            String app = configurator.getUrl().getParameter(ConfiguratorConfig.SCOPE_APPLICATION);
                            if (StringUtils.isEmpty(app) || StringUtils.isEquals(ApplicationModel.getApplication(), app)) {
                                filtered.add(configurator);
                            }
                        }
                    } else {
                        /**
                         * Application scope level:
                         * we don't know which interfaces will work
                         * and will have to wait until the Configurator#configure phase.
                         */
                        filtered.add(configurator);
                    }
                }
                this.configurators = filtered;
            }
        }
    }

    protected abstract void notifyOverrides();

    public List<Configurator> getConfigurators() {
        return configurators;
    }

    public void setConfigurators(List<Configurator> configurators) {
        this.configurators = configurators;
    }
}
