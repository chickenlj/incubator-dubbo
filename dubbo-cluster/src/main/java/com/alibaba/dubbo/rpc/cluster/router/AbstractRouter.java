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
package com.alibaba.dubbo.rpc.cluster.router;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.configcenter.DynamicConfiguration;
import com.alibaba.dubbo.configcenter.DynamicConfigurationInstance;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.cluster.Router;

import java.util.List;

public abstract class AbstractRouter implements Router {
    protected int priority = DEFAULT_PRIORITY;
    protected boolean force = false;
    protected URL url;

    protected DynamicConfiguration ruleRepository;

    public AbstractRouter(URL url) {
        this.ruleRepository = DynamicConfigurationInstance.getInstance();
        this.url = url;
    }

    public AbstractRouter() {
    }

    public <T> void notify(List<Invoker<T>> invokers) {

    }

    @Override
    public URL getUrl() {
        return url;
    }

    public void setUrl(URL url) {
        this.url = url;
    }

    @Override
    public boolean isRuntime() {
        return true;
    }

    @Override
    public boolean isForce() {
        return force;
    }

    public void setForce(boolean force) {
        this.force = force;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public DynamicConfiguration getRuleRepository() {
        return ruleRepository;
    }

    @Override
    public int compareTo(Router o) {
        if (o == null) {
            throw new IllegalArgumentException();
        }
        int x = this.getPriority(), y = o.getPriority();
        return (x < y) ? -1 : ((x == y) ? 0 : 1);
    }

}
