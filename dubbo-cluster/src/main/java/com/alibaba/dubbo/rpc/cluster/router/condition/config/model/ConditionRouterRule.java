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
package com.alibaba.dubbo.rpc.cluster.router.condition.config.model;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.alibaba.dubbo.common.Constants.CONDITIONS_KEY;

public class ConditionRouterRule extends AbstractRouterRule {
    private List<String> conditions;

    @SuppressWarnings("unchecked")
    public static ConditionRouterRule parseFromMap(Map<String, Object> map) {
        ConditionRouterRule conditionRouterRule = new ConditionRouterRule();
        conditionRouterRule.parseFromMap0(map);

        Object conditions = map.get(CONDITIONS_KEY);
        if (conditions != null && List.class.isAssignableFrom(conditions.getClass())) {
            List<String> rawConditions = new ArrayList<String>();
            for (Object obj : (List<Object>) conditions) {
                rawConditions.add(String.valueOf(obj));
            }
            conditionRouterRule.setConditions(rawConditions);
        }

        return conditionRouterRule;
    }

    public ConditionRouterRule() {
    }

    public List<String> getConditions() {
        return conditions;
    }

    public void setConditions(List<String> conditions) {
        this.conditions = conditions;
    }
}
