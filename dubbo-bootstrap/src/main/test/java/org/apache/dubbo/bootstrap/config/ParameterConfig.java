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
package org.apache.dubbo.bootstrap.config;

import org.apache.dubbo.config.support.Parameter;

import java.util.HashMap;
import java.util.Map;

/**
 *
 */
public class ParameterConfig {
    private int number;
    private String name;
    private int age;
    private String secret;

    ParameterConfig() {
    }

    ParameterConfig(int number, String name, int age, String secret) {
        this.number = number;
        this.name = name;
        this.age = age;
        this.secret = secret;
    }

    @Parameter(key = "num", append = true)
    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    @Parameter(key = "naming", append = true, escaped = true, required = true)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    @Parameter(excluded = true)
    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public Map getParameters() {
        Map<String, String> map = new HashMap<String, String>();
        map.put("key.1", "one");
        map.put("key-2", "two");
        return map;
    }
}
