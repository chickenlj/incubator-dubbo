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
package org.apache.dubbo.rpc;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;


/**
 * RPC invoke result. (API, Prototype, NonThreadSafe)
 *
 * @serial Don't change the class name and package name.
 * @see org.apache.dubbo.rpc.Invoker#invoke(Invocation)
 * @see AppResponse
 */
public interface Result extends Serializable {

    /**
     * Get invoke result.
     *
     * @return result. if no result return null.
     */
    Object getValue();

    void setValue(Object value);

    /**
     * Get exception.
     *
     * @return exception. if no exception return null.
     */
    Throwable getException();

    void setException(Throwable t);

    /**
     * Has exception.
     *
     * @return has exception.
     */
    boolean hasException();

    /**
     * Recreate.
     * <p>
     * <code>
     * if (hasException()) {
     * throw getException();
     * } else {
     * return getValue();
     * }
     * </code>
     *
     * @return result.
     * @throws if has exception throw it.
     */
    Object recreate() throws Throwable;

    /**
     * get attachments.
     *
     * @return attachments.
     */
    Map<String, String> getAttachments();

    /**
     * Add the specified map to existing attachments in this instance.
     *
     * @param map
     */
    void addAttachments(Map<String, String> map);

    /**
     * Replace the existing attachments with the specified param.
     *
     * @param map
     */
    void setAttachments(Map<String, String> map);

    /**
     * get attachment by key.
     *
     * @return attachment value.
     */
    String getAttachment(String key);

    /**
     * get attachment by key with default value.
     *
     * @return attachment value.
     */
    String getAttachment(String key, String defaultValue);

    void setAttachment(String key, String value);

    Result thenApplyWithContext(Function<AppResponse, AppResponse> fn);

    <U> CompletableFuture<U> thenApply(Function<Result, ? extends U> fn);

    Result get() throws InterruptedException, ExecutionException;

}