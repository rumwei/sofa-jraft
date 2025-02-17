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
package com.alipay.sofa.jraft.util;

import com.alipay.sofa.jraft.rpc.RaftRpcFactory;
import com.alipay.sofa.jraft.rpc.RpcResponseFactory;

/**
 * @author jiachun.fjc
 */
public class RpcFactoryHelper {

    // 采用SPI，以便可以更换RPC服务，目前可选的有BoltRpc与GRpc，具体使用哪个开发者可以通过配置
    // jraft-core/resources/META-INF.services/com.alipay.sofa.jraft.rpc.RaftRpcFactory
    // 来自定义。默认是BoltRaftRpcFactory
    private static final RaftRpcFactory RPC_FACTORY = JRaftServiceLoader.load(RaftRpcFactory.class).first();

    public static RaftRpcFactory rpcFactory() {
        return RPC_FACTORY;
    }

    public static RpcResponseFactory responseFactory() {
        return RPC_FACTORY.getRpcResponseFactory();
    }
}
