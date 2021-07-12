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
package com.alipay.sofa.jraft.rhea.rpc;

import com.alipay.remoting.InvokeContext;
import com.alipay.remoting.serialization.SerializerManager;

/**
 * @author jiachun.fjc
 */
public final class ExtSerializerSupports {

    private static final InvokeContext INVOKE_CONTEXT = new InvokeContext();

    public static byte PROTO_STUFF = 2;

    static {
        SerializerManager.addSerializer(PROTO_STUFF, ProtostuffSerializer.INSTANCE);
        INVOKE_CONTEXT.put(InvokeContext.BOLT_CUSTOM_SERIALIZER, PROTO_STUFF);
        INVOKE_CONTEXT.put(InvokeContext.BOLT_CRC_SWITCH, false);
    }

    public static void init() {
        // Will execute the code first of the static block
    }

    public static InvokeContext getInvokeContext() {
        return INVOKE_CONTEXT;
    }

    private ExtSerializerSupports() {
    }
}
