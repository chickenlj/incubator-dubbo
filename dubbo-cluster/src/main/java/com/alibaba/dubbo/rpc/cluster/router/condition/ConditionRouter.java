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
package com.alibaba.dubbo.rpc.cluster.router.condition;


import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.extension.ExtensionLoader;
import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.common.utils.NetUtils;
import com.alibaba.dubbo.common.utils.StringUtils;
import com.alibaba.dubbo.configcenter.CollectionUtils;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.cluster.router.AbstractRouter;
import com.alibaba.dubbo.rpc.cluster.router.condition.matcher.ConditionMatcher;
import com.alibaba.dubbo.rpc.cluster.router.condition.matcher.ConditionMatcherFactory;
import com.alibaba.dubbo.rpc.cluster.router.condition.matcher.pattern.ValuePattern;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.alibaba.dubbo.common.Constants.ENABLED_KEY;
import static com.alibaba.dubbo.common.Constants.FORCE_KEY;
import static com.alibaba.dubbo.common.Constants.RULE_KEY;
import static com.alibaba.dubbo.common.Constants.RUNTIME_KEY;

/**
 * Condition Router directs traffics matching the 'when condition' to a particular address subset determined by the 'then condition'.
 * One typical condition rule is like below, with
 * 1. the 'when condition' on the left side of '=>' contains matching rule like 'method=sayHello' and 'method=sayHi'
 * 2. the 'then condition' on the right side of '=>' contains matching rule like 'region=hangzhou' and 'address=*:20881'
 * <p>
 * By default, condition router support matching rules like 'foo=bar', 'foo=bar*', 'arguments[0]=bar', 'attachments[foo]=bar', 'attachments[foo]=1~100', etc.
 * It's also very easy to add customized matching rules by extending {@link ConditionMatcherFactory}
 * and {@link ValuePattern}
 * <p>
 * ---
 * scope: service
 * force: true
 * runtime: true
 * enabled: true
 * key: org.apache.dubbo.samples.governance.api.DemoService
 * conditions:
 * - method=sayHello => region=hangzhou
 * - method=sayHi => address=*:20881
 * ...
 */
public class ConditionRouter extends AbstractRouter {
    public static final String NAME = "condition";

    private static final Logger logger = LoggerFactory.getLogger(ConditionRouter.class);
    protected static final Pattern ROUTE_PATTERN = Pattern.compile("([&!=,]*)\\s*([^&!=,\\s]+)");
    protected Map<String, ConditionMatcher> whenCondition;
    protected Map<String, ConditionMatcher> thenCondition;
    protected List<ConditionMatcherFactory> matcherFactories;

    private final boolean enabled;

    public ConditionRouter(URL url, String rule, boolean force, boolean enabled) {
        super(url);
        this.setForce(force);
        this.enabled = enabled;
        matcherFactories = ExtensionLoader.getExtensionLoader(ConditionMatcherFactory.class).getActivateExtensions();
        if (enabled) {
            this.init(rule);
        }
    }

    public ConditionRouter(URL url) {
        super(url);
        this.setUrl(url);
        this.setForce(url.getParameter(FORCE_KEY, false));
        matcherFactories = ExtensionLoader.getExtensionLoader(ConditionMatcherFactory.class).getActivateExtensions();
        this.enabled = url.getParameter(ENABLED_KEY, true);
        if (enabled) {
            init(url.getParameterAndDecoded(RULE_KEY));
        }
    }

    public void init(String rule) {
        try {
            if (rule == null || rule.trim().length() == 0) {
                throw new IllegalArgumentException("Illegal route rule!");
            }
            rule = rule.replace("consumer.", "").replace("provider.", "");
            int i = rule.indexOf("=>");
            String whenRule = i < 0 ? null : rule.substring(0, i).trim();
            String thenRule = i < 0 ? rule.trim() : rule.substring(i + 2).trim();
            Map<String, ConditionMatcher> when = StringUtils.isBlank(whenRule) || "true".equals(whenRule) ? new HashMap<String, ConditionMatcher>() : parseRule(whenRule);
            Map<String, ConditionMatcher> then = StringUtils.isBlank(thenRule) || "false".equals(thenRule) ? null : parseRule(thenRule);
            // NOTE: It should be determined on the business level whether the `When condition` can be empty or not.
            this.whenCondition = when;
            this.thenCondition = then;
        } catch (ParseException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private Map<String, ConditionMatcher> parseRule(String rule)
            throws ParseException {
        Map<String, ConditionMatcher> condition = new HashMap<String, ConditionMatcher>();
        if (StringUtils.isBlank(rule)) {
            return condition;
        }
        // Key-Value pair, stores both match and mismatch conditions
        ConditionMatcher matcherPair = null;
        // Multiple values
        Set<String> values = null;
        final Matcher matcher = ROUTE_PATTERN.matcher(rule);
        while (matcher.find()) { // Try to match one by one
            String separator = matcher.group(1);
            String content = matcher.group(2);
            // Start part of the condition expression.
            if (StringUtils.isEmpty(separator)) {
                matcherPair = this.getMatcher(content);
                condition.put(content, matcherPair);
            }
            // The KV part of the condition expression
            else if ("&".equals(separator)) {
                if (condition.get(content) == null) {
                    matcherPair = this.getMatcher(content);
                    condition.put(content, matcherPair);
                } else {
                    matcherPair = condition.get(content);
                }
            }
            // The Value in the KV part.
            else if ("=".equals(separator)) {
                if (matcherPair == null) {
                    throw new ParseException("Illegal route rule \""
                            + rule + "\", The error char '" + separator
                            + "' at index " + matcher.start() + " before \""
                            + content + "\".", matcher.start());
                }

                values = matcherPair.getMatches();
                values.add(content);
            }
            // The Value in the KV part.
            else if ("!=".equals(separator)) {
                if (matcherPair == null) {
                    throw new ParseException("Illegal route rule \""
                            + rule + "\", The error char '" + separator
                            + "' at index " + matcher.start() + " before \""
                            + content + "\".", matcher.start());
                }

                values = matcherPair.getMismatches();
                values.add(content);
            }
            // The Value in the KV part, if Value have more than one items.
            else if (",".equals(separator)) { // Should be separated by ','
                if (values == null || values.isEmpty()) {
                    throw new ParseException("Illegal route rule \""
                            + rule + "\", The error char '" + separator
                            + "' at index " + matcher.start() + " before \""
                            + content + "\".", matcher.start());
                }
                values.add(content);
            } else {
                throw new ParseException("Illegal route rule \"" + rule
                        + "\", The error char '" + separator + "' at index "
                        + matcher.start() + " before \"" + content + "\".", matcher.start());
            }
        }
        return condition;
    }

    @Override
    public <T> List<Invoker<T>> route(List<Invoker<T>> invokers, URL url, Invocation invocation)
            throws RpcException {
        if (!enabled) {
            return invokers;
        }

        if (CollectionUtils.isEmpty(invokers)) {
            return invokers;
        }
        try {
            if (!matchWhen(url, invocation)) {
                return invokers;
            }
            List<Invoker<T>> result = new ArrayList<Invoker<T>>();
            if (thenCondition == null) {
                logger.warn("The current consumer in the service blacklist. consumer: " + NetUtils.getLocalHost() + ", service: " + url.getServiceKey());
                return result;
            }
            for (Invoker<T> invoker : invokers) {
                if (matchThen(invoker.getUrl(), url)) {
                    result.add(invoker);
                }
            }
            if (!result.isEmpty()) {
                return result;
            } else if (force) {
                logger.warn("The route result is empty and force execute. consumer: " + NetUtils.getLocalHost() + ", service: " + url.getServiceKey() + ", router: " + url.getParameterAndDecoded(RULE_KEY));
                return result;
            }
        } catch (Throwable t) {
            logger.error("Failed to execute condition router rule: " + getUrl() + ", invokers: " + invokers + ", cause: " + t.getMessage(), t);
        }
        return invokers;
    }

    @Override
    public boolean isRuntime() {
        // We always return true for previously defined Router, that is, old Router doesn't support cache anymore.
//        return true;
        return this.getUrl().getParameter(RUNTIME_KEY, false);
    }

    private ConditionMatcher getMatcher(String key) {
        for (ConditionMatcherFactory factory : matcherFactories) {
            if (factory.shouldMatch(key)) {
                return factory.createMatcher(key);
            }
        }
        return ExtensionLoader.getExtensionLoader(ConditionMatcherFactory.class).getExtension("param").createMatcher(key);
    }

    boolean matchWhen(URL url, Invocation invocation) {
        if (CollectionUtils.isEmptyMap(whenCondition)) {
            return true;
        }

        return doMatch(url, null, invocation, whenCondition, true);
    }

    private boolean matchThen(URL url, URL param) {
        if (CollectionUtils.isEmptyMap(thenCondition)) {
            return false;
        }

        return doMatch(url, param, null, thenCondition, false);
    }

    private boolean doMatch(URL url, URL param, Invocation invocation, Map<String, ConditionMatcher> conditions, boolean isWhenCondition) {
        Map<String, String> sample = url.toOriginalMap();
        for (Map.Entry<String, ConditionMatcher> entry : conditions.entrySet()) {
            ConditionMatcher matchPair = entry.getValue();
            if (!matchPair.isMatch(sample, param, invocation, isWhenCondition)) {
                return false;
            }
        }
        return true;
    }
}
