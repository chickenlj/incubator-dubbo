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
package org.apache.dubbo.bootstrap;

import junit.framework.TestCase;
import org.apache.dubbo.bootstrap.config.PropertiesConfig;
import org.apache.dubbo.config.utils.ConfigConverter;
import org.junit.Test;

import java.util.Map;

/**
 *
 */
public class CompositeConfigurationTest {

    @Test
    public void testAppendProperties1() throws Exception {
        try {
            System.setProperty("dubbo.properties.i", "1");
            System.setProperty("dubbo.properties.c", "c");
            System.setProperty("dubbo.properties.b", "2");
            System.setProperty("dubbo.properties.d", "3");
            System.setProperty("dubbo.properties.f", "4");
            System.setProperty("dubbo.properties.l", "5");
            System.setProperty("dubbo.properties.s", "6");
            System.setProperty("dubbo.properties.str", "dubbo");
            System.setProperty("dubbo.properties.bool", "true");
            PropertiesConfig config = new PropertiesConfig();
            Map<String, String> map = ConfigConverter.configToMap(config, null);
            TestCase.assertEquals("1", map.get("i"));
            TestCase.assertEquals("c", map.get("c"));
            TestCase.assertEquals("2", map.get("b"));
            TestCase.assertEquals("3", map.get("d"));
            TestCase.assertEquals("4", map.get("f"));
            TestCase.assertEquals("5", map.get("l"));
            TestCase.assertEquals("6", map.get("s"));
            TestCase.assertEquals("dubbo", map.get("str"));
            TestCase.assertEquals("true", map.get("bool"));
        } finally {
            System.clearProperty("dubbo.properties.i");
            System.clearProperty("dubbo.properties.c");
            System.clearProperty("dubbo.properties.b");
            System.clearProperty("dubbo.properties.d");
            System.clearProperty("dubbo.properties.f");
            System.clearProperty("dubbo.properties.l");
            System.clearProperty("dubbo.properties.s");
            System.clearProperty("dubbo.properties.str");
            System.clearProperty("dubbo.properties.bool");
        }
    }

    @Test
    public void testAppendProperties2() throws Exception {
        try {
            System.setProperty("dubbo.properties.two.i", "2");
            PropertiesConfig config = new PropertiesConfig("two");
            Map<String, String> map = ConfigConverter.configToMap(config, null);
            TestCase.assertEquals(2, map.get("i"));
        } finally {
            System.clearProperty("dubbo.properties.two.i");
        }
    }
/*
    @Test
    public void testAppendProperties3() throws Exception {
        try {
            Properties p = new Properties();
            p.put("dubbo.properties.str", "dubbo");
            ConfigUtils.setProperties(p);
            PropertiesConfig config = new PropertiesConfig();
            AbstractConfig.appendProperties(config);
            TestCase.assertEquals("dubbo", config.getStr());
        } finally {
            System.clearProperty(Constants.DUBBO_PROPERTIES_KEY);
            ConfigUtils.setProperties(null);
        }
    }

    @Test
    public void testAppendParameters1() throws Exception {
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("default.num", "one");
        parameters.put("num", "ONE");
        AbstractConfig.appendParameters(parameters, new ParameterConfig(1, "hello/world", 30, "password"), "prefix");
        TestCase.assertEquals("one", parameters.get("prefix.key.1"));
        TestCase.assertEquals("two", parameters.get("prefix.key.2"));
        TestCase.assertEquals("ONE,one,1", parameters.get("prefix.num"));
        TestCase.assertEquals("hello%2Fworld", parameters.get("prefix.naming"));
        TestCase.assertEquals("30", parameters.get("prefix.age"));
        TestCase.assertFalse(parameters.containsKey("prefix.key-2"));
        TestCase.assertFalse(parameters.containsKey("prefix.secret"));
    }

    @Test(expected = IllegalStateException.class)
    public void testAppendParameters2() throws Exception {
        Map<String, String> parameters = new HashMap<String, String>();
        AbstractConfig.appendParameters(parameters, new ParameterConfig());
    }

    @Test
    public void testAppendParameters3() throws Exception {
        Map<String, String> parameters = new HashMap<String, String>();
        AbstractConfig.appendParameters(parameters, null);
        TestCase.assertTrue(parameters.isEmpty());
    }

    @Test
    public void testAppendParameters4() throws Exception {
        Map<String, String> parameters = new HashMap<String, String>();
        AbstractConfig.appendParameters(parameters, new ParameterConfig(1, "hello/world", 30, "password"));
        TestCase.assertEquals("one", parameters.get("key.1"));
        TestCase.assertEquals("two", parameters.get("key.2"));
        TestCase.assertEquals("1", parameters.get("num"));
        TestCase.assertEquals("hello%2Fworld", parameters.get("naming"));
        TestCase.assertEquals("30", parameters.get("age"));
    }*/
}
