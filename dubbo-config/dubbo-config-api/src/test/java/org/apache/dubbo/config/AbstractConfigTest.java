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

import junit.framework.TestCase;
import org.apache.dubbo.config.api.Greeting;
import org.apache.dubbo.config.model.AnnotationConfig;
import org.apache.dubbo.config.model.AttributeConfig;
import org.apache.dubbo.config.model.Config;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class AbstractConfigTest {

    @Test
    public void testAppendAttributes1() throws Exception {
        Map<Object, Object> parameters = new HashMap<Object, Object>();
        AbstractConfig.appendAttributes(parameters, new AttributeConfig('l', true, (byte) 0x01), "prefix");
        TestCase.assertEquals('l', parameters.get("prefix.let"));
        TestCase.assertEquals(true, parameters.get("prefix.activate"));
        TestCase.assertFalse(parameters.containsKey("prefix.flag"));
    }

    @Test
    public void testAppendAttributes2() throws Exception {
        Map<Object, Object> parameters = new HashMap<Object, Object>();
        AbstractConfig.appendAttributes(parameters, new AttributeConfig('l', true, (byte) 0x01));
        TestCase.assertEquals('l', parameters.get("let"));
        TestCase.assertEquals(true, parameters.get("activate"));
        TestCase.assertFalse(parameters.containsKey("flag"));
    }

    @Test(expected = IllegalStateException.class)
    public void checkExtension() throws Exception {
        AbstractConfig.checkExtension("org.apache.dubbo.config.api.Greeting", "hello", "world");
    }

    @Test(expected = IllegalStateException.class)
    public void checkMultiExtension1() throws Exception {
        AbstractConfig.checkMultiExtension("org.apache.dubbo.config.api.Greeting", "hello", "default,world");
    }

    @Test(expected = IllegalStateException.class)
    public void checkMultiExtension2() throws Exception {
        AbstractConfig.checkMultiExtension("org.apache.dubbo.config.api.Greeting", "hello", "default,-world");
    }

    @Test(expected = IllegalStateException.class)
    public void checkLength() throws Exception {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i <= 200; i++) {
            builder.append("a");
        }
        AbstractConfig.checkLength("hello", builder.toString());
    }

    @Test(expected = IllegalStateException.class)
    public void checkPathLength() throws Exception {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i <= 200; i++) {
            builder.append("a");
        }
        AbstractConfig.checkPathLength("hello", builder.toString());
    }

    @Test(expected = IllegalStateException.class)
    public void checkName() throws Exception {
        AbstractConfig.checkName("hello", "world%");
    }

    @Test
    public void checkNameHasSymbol() throws Exception {
        try {
            AbstractConfig.checkNameHasSymbol("hello", ":*,/-0123abcdABCD");
        } catch (Exception e) {
            TestCase.fail("the value should be legal.");
        }
    }

    @Test
    public void checkKey() throws Exception {
        try {
            AbstractConfig.checkKey("hello", "*,-0123abcdABCD");
        } catch (Exception e) {
            TestCase.fail("the value should be legal.");
        }
    }

    @Test
    public void checkMultiName() throws Exception {
        try {
            AbstractConfig.checkMultiName("hello", ",-._0123abcdABCD");
        } catch (Exception e) {
            TestCase.fail("the value should be legal.");
        }
    }

    @Test
    public void checkPathName() throws Exception {
        try {
            AbstractConfig.checkPathName("hello", "/-$._0123abcdABCD");
        } catch (Exception e) {
            TestCase.fail("the value should be legal.");
        }
    }

    @Test
    public void checkMethodName() throws Exception {
        try {
            AbstractConfig.checkMethodName("hello", "abcdABCD0123abcd");
        } catch (Exception e) {
            TestCase.fail("the value should be legal.");
        }

        try {
            AbstractConfig.checkMethodName("hello", "0a");
            TestCase.fail("the value should be illegal.");
        } catch (Exception e) {
            // ignore
        }
    }

    @Test
    public void checkParameterName() throws Exception {
        Map<String, String> parameters = Collections.singletonMap("hello", ":*,/-._0123abcdABCD");
        try {
            AbstractConfig.checkParameterName(parameters);
        } catch (Exception e) {
            TestCase.fail("the value should be legal.");
        }
    }

    @Test
    @Config(interfaceClass = Greeting.class, filter = {"f1, f2"}, listener = {"l1, l2"},
            parameters = {"k1", "v1", "k2", "v2"})
    public void appendAnnotation() throws Exception {
        Config config = getClass().getMethod("appendAnnotation").getAnnotation(Config.class);
        AnnotationConfig annotationConfig = new AnnotationConfig();
        annotationConfig.appendAnnotation(Config.class, config);
        TestCase.assertSame(Greeting.class, annotationConfig.getInterface());
        TestCase.assertEquals("f1, f2", annotationConfig.getFilter());
        TestCase.assertEquals("l1, l2", annotationConfig.getListener());
        TestCase.assertEquals(2, annotationConfig.getParameters().size());
        TestCase.assertEquals("v1", annotationConfig.getParameters().get("k1"));
        TestCase.assertEquals("v2", annotationConfig.getParameters().get("k2"));
        TestCase.assertEquals("<dubbo:annotation filter=\"f1, f2\" listener=\"l1, l2\" />",
                annotationConfig.toString());
    }
}
