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
package com.alibaba.dubbo.remoting.http;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.utils.ProtocolUtils;

import javax.servlet.http.HttpServletRequest;

/**
 * @author ken.lj
 * @date 16/05/2018
 */
public abstract class AbstractHttpHandler implements HttpHandler {

    protected String getPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String generic = request.getParameter(Constants.GENERIC_KEY);
        if (ProtocolUtils.isGeneric(generic)) {
            path += ("/" + Constants.GENERIC_KEY);
        }
        return path;

    }

}
