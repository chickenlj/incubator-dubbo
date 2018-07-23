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
package org.apache.dubbo.config;

import org.apache.dubbo.common.Constants;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.ReflectUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.config.support.Parameter;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility methods and public methods for parsing configuration
 *
 * @export
 */
public abstract class AbstractConfig implements Serializable {

    protected static final Logger logger = LoggerFactory.getLogger(AbstractConfig.class);
    private static final long serialVersionUID = 4267533505537413570L;
    private static final int MAX_LENGTH = 200;

    private static final int MAX_PATH_LENGTH = 200;

    private static final Pattern PATTERN_NAME = Pattern.compile("[\\-._0-9a-zA-Z]+");

    private static final Pattern PATTERN_MULTI_NAME = Pattern.compile("[,\\-._0-9a-zA-Z]+");

    private static final Pattern PATTERN_METHOD_NAME = Pattern.compile("[a-zA-Z][0-9a-zA-Z]*");

    private static final Pattern PATTERN_PATH = Pattern.compile("[/\\-$._0-9a-zA-Z]+");

    private static final Pattern PATTERN_NAME_HAS_SYMBOL = Pattern.compile("[:*,/\\-._0-9a-zA-Z]+");

    private static final Pattern PATTERN_KEY = Pattern.compile("[*,\\-._0-9a-zA-Z]+");
    private static final String[] SUFFIXES = new String[]{"Config", "Bean"};
    private volatile Map<String, Object> metaData;

    protected String id;

    private static String getTagName(Class<?> cls) {
        String tag = cls.getSimpleName();
        for (String suffix : SUFFIXES) {
            if (tag.endsWith(suffix)) {
                tag = tag.substring(0, tag.length() - suffix.length());
                break;
            }
        }
        tag = tag.toLowerCase();
        return tag;
    }

    public static void appendAttributes(Map<Object, Object> parameters, Object config) {
        appendAttributes(parameters, config, null);
    }

    public static void appendAttributes(Map<Object, Object> parameters, Object config, String prefix) {
        if (config == null) {
            return;
        }
        Method[] methods = config.getClass().getMethods();
        for (Method method : methods) {
            try {
                String name = method.getName();
                if ((name.startsWith("get") || name.startsWith("is"))
                        && !"getClass".equals(name)
                        && Modifier.isPublic(method.getModifiers())
                        && method.getParameterTypes().length == 0
                        && isPrimitive(method.getReturnType())) {
                    Parameter parameter = method.getAnnotation(Parameter.class);
                    if (parameter == null || !parameter.attribute())
                        continue;
                    String key;
                    parameter.key();
                    if (parameter.key().length() > 0) {
                        key = parameter.key();
                    } else {
                        int i = name.startsWith("get") ? 3 : 2;
                        key = name.substring(i, i + 1).toLowerCase() + name.substring(i + 1);
                    }
                    Object value = method.invoke(config);
                    if (value != null) {
                        if (prefix != null && prefix.length() > 0) {
                            key = prefix + "." + key;
                        }
                        parameters.put(key, value);
                    }
                }
            } catch (Exception e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }
    }

    private static boolean isPrimitive(Class<?> type) {
        return type.isPrimitive()
                || type == String.class
                || type == Character.class
                || type == Boolean.class
                || type == Byte.class
                || type == Short.class
                || type == Integer.class
                || type == Long.class
                || type == Float.class
                || type == Double.class
                || type == Object.class;
    }

    protected static void checkExtension(String className, String property, String value) {
        checkName(property, value);
        if (value != null && value.length() > 0
                && !ExtensionLoader.getExtensionLoader(className).hasExtension(value)) {
            throw new IllegalStateException("No such extension " + value + " for " + property + "/" + className);
        }
    }

    public static void checkMultiExtension(String className, String property, String value) {
        checkMultiName(property, value);
        if (value != null && value.length() > 0) {
            String[] values = value.split("\\s*[,]+\\s*");
            for (String v : values) {
                if (v.startsWith(Constants.REMOVE_VALUE_PREFIX)) {
                    v = v.substring(1);
                }
                if (Constants.DEFAULT_KEY.equals(v)) {
                    continue;
                }
                if (!ExtensionLoader.getExtensionLoader(className).hasExtension(v)) {
                    throw new IllegalStateException("No such extension " + v + " for " + property + "/" + className);
                }
            }
        }
    }

    protected static void checkLength(String property, String value) {
        checkProperty(property, value, MAX_LENGTH, null);
    }

    protected static void checkPathLength(String property, String value) {
        checkProperty(property, value, MAX_PATH_LENGTH, null);
    }

    protected static void checkName(String property, String value) {
        checkProperty(property, value, MAX_LENGTH, PATTERN_NAME);
    }

    protected static void checkNameHasSymbol(String property, String value) {
        checkProperty(property, value, MAX_LENGTH, PATTERN_NAME_HAS_SYMBOL);
    }

    protected static void checkKey(String property, String value) {
        checkProperty(property, value, MAX_LENGTH, PATTERN_KEY);
    }

    protected static void checkMultiName(String property, String value) {
        checkProperty(property, value, MAX_LENGTH, PATTERN_MULTI_NAME);
    }

    protected static void checkPathName(String property, String value) {
        checkProperty(property, value, MAX_PATH_LENGTH, PATTERN_PATH);
    }

    protected static void checkMethodName(String property, String value) {
        checkProperty(property, value, MAX_LENGTH, PATTERN_METHOD_NAME);
    }

    protected static void checkParameterName(Map<String, String> parameters) {
        if (parameters == null || parameters.size() == 0) {
            return;
        }
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            checkNameHasSymbol(entry.getKey(), entry.getValue());
        }
    }

    protected static void checkProperty(String property, String value, int maxlength, Pattern pattern) {
        if (value == null || value.length() == 0) {
            return;
        }
        if (value.length() > maxlength) {
            throw new IllegalStateException("Invalid " + property + "=\"" + value + "\" is longer than " + maxlength);
        }
        if (pattern != null) {
            Matcher matcher = pattern.matcher(value);
            if (!matcher.matches()) {
                throw new IllegalStateException("Invalid " + property + "=\"" + value + "\" contains illegal " +
                        "character, only digit, letter, '-', '_' or '.' is legal.");
            }
        }
    }

    @Parameter(excluded = true)
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    protected void appendAnnotation(Class<?> annotationClass, Object annotation) {
        Method[] methods = annotationClass.getMethods();
        for (Method method : methods) {
            if (method.getDeclaringClass() != Object.class
                    && method.getReturnType() != void.class
                    && method.getParameterTypes().length == 0
                    && Modifier.isPublic(method.getModifiers())
                    && !Modifier.isStatic(method.getModifiers())) {
                try {
                    String property = method.getName();
                    if ("interfaceClass".equals(property) || "interfaceName".equals(property)) {
                        property = "interface";
                    }
                    String setter = "set" + property.substring(0, 1).toUpperCase() + property.substring(1);
                    Object value = method.invoke(annotation);
                    if (value != null && !value.equals(method.getDefaultValue())) {
                        Class<?> parameterType = ReflectUtils.getBoxedClass(method.getReturnType());
                        if ("filter".equals(property) || "listener".equals(property)) {
                            parameterType = String.class;
                            value = StringUtils.join((String[]) value, ",");
                        } else if ("parameters".equals(property)) {
                            parameterType = Map.class;
                            value = CollectionUtils.toStringMap((String[]) value);
                        }
                        try {
                            Method setterMethod = getClass().getMethod(setter, parameterType);
                            setterMethod.invoke(this, value);
                        } catch (NoSuchMethodException e) {
                            // ignore
                        }
                    }
                } catch (Throwable e) {
                    logger.error(e.getMessage(), e);
                }
            }
        }
    }

    @Override
    public String toString() {
        try {
            StringBuilder buf = new StringBuilder();
            buf.append("<dubbo:");
            buf.append(getTagName(getClass()));
            Method[] methods = getClass().getMethods();
            for (Method method : methods) {
                try {
                    String name = method.getName();
                    if ((name.startsWith("get") || name.startsWith("is"))
                            && !"getClass".equals(name) && !"get".equals(name) && !"is".equals(name)
                            && Modifier.isPublic(method.getModifiers())
                            && method.getParameterTypes().length == 0
                            && isPrimitive(method.getReturnType())) {
                        int i = name.startsWith("get") ? 3 : 2;
                        String key = name.substring(i, i + 1).toLowerCase() + name.substring(i + 1);
                        Object value = method.invoke(this);
                        if (value != null) {
                            buf.append(" ");
                            buf.append(key);
                            buf.append("=\"");
                            buf.append(value);
                            buf.append("\"");
                        }
                    }
                } catch (Exception e) {
                    logger.warn(e.getMessage(), e);
                }
            }
            buf.append(" />");
            return buf.toString();
        } catch (Throwable t) {
            logger.warn(t.getMessage(), t);
            return super.toString();
        }
    }

    /**
     * Should be called after Config was fully initialized.
     *
     * @param prefix
     * @return
     */
    public Map<String, Object> getMetaData(String prefix) {
        if (metaData != null) {
            return metaData;
        }
        synchronized (this) {
            if (metaData != null) {
                return metaData;
            }
            metaData = new HashMap<>();
            Method[] methods = this.getClass().getMethods();
            for (Method method : methods) {
                try {
                    String name = method.getName();
                    if ((name.startsWith("get") || name.startsWith("is"))
                            && !name.equals("get")
                            && !"getClass".equals(name)
                            && Modifier.isPublic(method.getModifiers())
                            && method.getParameterTypes().length == 0
                            && isPrimitive(method.getReturnType())) {
                        int i = name.startsWith("get") ? 3 : 2;
                        String prop = StringUtils.camelToSplitName(name.substring(i, i + 1).toLowerCase() + name.substring(i + 1), ".");
                        String key;
                        Parameter parameter = method.getAnnotation(Parameter.class);
                        if (parameter != null && parameter.key().length() > 0) {
                            key = parameter.key();
                        } else {
                            key = prop;
                        }
                        if (method.getReturnType() == Object.class || parameter != null && parameter.excluded()) {
                            metaData.put(key, null);
                            continue;
                        }
                        Object value = method.invoke(this);
                        String str = String.valueOf(value).trim();
                        if (value != null && str.length() > 0) {
                            if (parameter != null && parameter.escaped()) {
                                str = URL.encode(str);
                            }
                            if (parameter != null && parameter.append()) {
                                String pre = String.valueOf(metaData.get(Constants.DEFAULT_KEY + "." + key));
                                if (pre != null && pre.length() > 0) {
                                    str = pre + "," + str;
                                }
                                pre = String.valueOf(metaData.get(key));
                                if (pre != null && pre.length() > 0) {
                                    str = pre + "," + str;
                                }
                            }
                            if (prefix != null && prefix.length() > 0) {
                                key = prefix + "." + key;
                            }
                            metaData.put(key, str);
                        } else {
                            metaData.put(key, null);
                        }
                        // TODO check required somewhere else.
                        /*else if (parameter != null && parameter.required()) {
                            throw new IllegalStateException(this.getClass().getSimpleName() + "." + key + " == null");
                        }*/
                    } else if ("getParameters".equals(name)
                            && Modifier.isPublic(method.getModifiers())
                            && method.getParameterTypes().length == 0
                            && method.getReturnType() == Map.class) {
                        Map<String, String> map = (Map<String, String>) method.invoke(this, new Object[0]);
                        if (map != null && map.size() > 0) {
                            String pre = (prefix != null && prefix.length() > 0 ? prefix + "." : "");
                            for (Map.Entry<String, String> entry : map.entrySet()) {
                                metaData.put(pre + entry.getKey().replace('-', '.'), entry.getValue());
                            }
                        }
                    }
                } catch (Exception e) {
                    System.out.println(this.getClass().getName());
                    System.out.println(method.getName());
                    throw new IllegalStateException(e.getMessage(), e);
                }
            }
        }
        return metaData;
    }

}
