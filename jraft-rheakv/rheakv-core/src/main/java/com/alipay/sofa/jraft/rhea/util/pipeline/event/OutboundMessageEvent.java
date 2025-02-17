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
package com.alipay.sofa.jraft.rhea.util.pipeline.event;

/**
 * The default outbound {@link MessageEvent} implementation.
 *
 * @author jiachun.fjc
 */
public abstract class OutboundMessageEvent<T> implements MessageEvent<T> {

    private final long invokeId;
    private final T message;

    public OutboundMessageEvent(long invokeId, T message) {
        this.invokeId = invokeId;
        this.message = message;
    }

    @Override
    public long getInvokeId() {
        return invokeId;
    }

    @Override
    public T getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "OutboundMessageEvent{" + "invokeId=" + invokeId + ", message=" + message + '}';
    }
}
