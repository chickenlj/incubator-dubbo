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

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import static java.lang.reflect.Modifier.isFinal;
import static java.lang.reflect.Modifier.isPublic;
import static java.lang.reflect.Modifier.isStatic;

public class ConfigcenterUtils {
    public static DynamicConfigurationFactory getDynamicConfigurationFactory(String name) {
        Class<DynamicConfigurationFactory> factoryClass = DynamicConfigurationFactory.class;
        ExtensionLoader<DynamicConfigurationFactory> loader = ExtensionLoader.getExtensionLoader(factoryClass);
        return loader.getExtension(name);
    }

    public static Set<String> getConstantFieldValues(Class<?> targetClass) {
        Set<String> params = new HashSet<String>();
        for (Field f : targetClass.getFields()) {
            if (isStatic(f.getModifiers()) && isPublic(f.getModifiers()) && isFinal(f.getModifiers())) {
                Object value = getFieldValue(null, f);
                if (value instanceof String) {
                    params.add((String) value);
                }
            }
        }
        return params;
    }

    static <T> T getFieldValue(Object object, Field field) {
        boolean accessible = field.isAccessible();
        Object value = null;
        try {
            if (!accessible) {
                field.setAccessible(true);
            }
            value = field.get(object);
        } catch (IllegalAccessException ignored) {
        } finally {
            field.setAccessible(accessible);
        }
        return (T) value;
    }

    /**
     * The format is '{interfaceName}:[version]:[group]'
     *
     * @return
     */
    public static String getRuleKey(URL url) {
        return url.getColonSeparatedKey();
    }

    public static String getDefaultGroup() {
        return "dubbo";
    }

    public static long getDefaultTimeout() {
        return -1L;
    }
}
