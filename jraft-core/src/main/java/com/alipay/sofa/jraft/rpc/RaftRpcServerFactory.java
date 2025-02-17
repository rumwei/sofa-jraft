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
package com.alipay.sofa.jraft.rpc;

import java.util.concurrent.Executor;

import com.alipay.sofa.jraft.rpc.impl.PingRequestProcessor;
import com.alipay.sofa.jraft.rpc.impl.cli.AddLearnersRequestProcessor;
import com.alipay.sofa.jraft.rpc.impl.cli.AddPeerRequestProcessor;
import com.alipay.sofa.jraft.rpc.impl.cli.ChangePeersRequestProcessor;
import com.alipay.sofa.jraft.rpc.impl.cli.GetLeaderRequestProcessor;
import com.alipay.sofa.jraft.rpc.impl.cli.GetPeersRequestProcessor;
import com.alipay.sofa.jraft.rpc.impl.cli.RemoveLearnersRequestProcessor;
import com.alipay.sofa.jraft.rpc.impl.cli.RemovePeerRequestProcessor;
import com.alipay.sofa.jraft.rpc.impl.cli.ResetLearnersRequestProcessor;
import com.alipay.sofa.jraft.rpc.impl.cli.ResetPeerRequestProcessor;
import com.alipay.sofa.jraft.rpc.impl.cli.SnapshotRequestProcessor;
import com.alipay.sofa.jraft.rpc.impl.cli.TransferLeaderRequestProcessor;
import com.alipay.sofa.jraft.rpc.impl.core.AppendEntriesRequestProcessor;
import com.alipay.sofa.jraft.rpc.impl.core.GetFileRequestProcessor;
import com.alipay.sofa.jraft.rpc.impl.core.InstallSnapshotRequestProcessor;
import com.alipay.sofa.jraft.rpc.impl.core.ReadIndexRequestProcessor;
import com.alipay.sofa.jraft.rpc.impl.core.RequestVoteRequestProcessor;
import com.alipay.sofa.jraft.rpc.impl.core.TimeoutNowRequestProcessor;
import com.alipay.sofa.jraft.util.Endpoint;
import com.alipay.sofa.jraft.util.RpcFactoryHelper;
import com.alipay.sofa.jraft.util.SlsLogUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Raft RPC server factory.
 * 该工厂类的方法都是静态方法，在首次调用该类的任意方法之前，都会先执行该类的static代码块来做初始化
 * 在该代码块中使用的ProtobufMsgFactory同样是静态类，在该类内部同样通过static代码块来做初始化
 */
public class RaftRpcServerFactory {

    private static final Logger logger = LoggerFactory.getLogger(RaftRpcServerFactory.class);

    static {
        SlsLogUtil.info("RaftRpcServerCreateAndInit", "traceId", "执行RaftRpcServerFactory类静态代码块");
        ProtobufMsgFactory.load();
    }

    /**
     * Creates a raft RPC server with default request executors.
     *
     * @param endpoint server address to bind
     * @return a rpc server instance
     */
    public static RpcServer createRaftRpcServer(final Endpoint endpoint) {
        return createRaftRpcServer(endpoint, null, null);
    }

    /**
     * Creates a raft RPC server with executors to handle requests.
     *
     * @param endpoint     server address to bind
     * @param raftExecutor executor to handle RAFT requests.
     * @param cliExecutor  executor to handle CLI service requests.
     * @return a rpc server instance
     */
    public static RpcServer createRaftRpcServer(final Endpoint endpoint, final Executor raftExecutor,
                                                final Executor cliExecutor) {
        logger.info("[rumwei] 通过spi创建RaftRpcFactory对象");
        RaftRpcFactory raftRpcFactory = RpcFactoryHelper.rpcFactory(); //BoltRaftRpcFactory
        logger.info("[rumwei] 借助RaftRpcFactory创建RpcServer实例");
        final RpcServer rpcServer = raftRpcFactory.createRpcServer(endpoint);
        addRaftRequestProcessors(rpcServer, raftExecutor, cliExecutor);
        return rpcServer;
    }

    /**
     * Adds RAFT and CLI service request processors with default executor.
     *
     * @param rpcServer rpc server instance
     */
    public static void addRaftRequestProcessors(final RpcServer rpcServer) {
        addRaftRequestProcessors(rpcServer, null, null);
    }

    /**
     * Adds RAFT and CLI service request processors.
     *
     * @param rpcServer    rpc server instance
     * @param raftExecutor executor to handle RAFT requests.
     * @param cliExecutor  executor to handle CLI service requests.
     */
    public static void addRaftRequestProcessors(final RpcServer rpcServer, final Executor raftExecutor,
                                                final Executor cliExecutor) {
        logger.info("[rumwei] 为RpcServer实例注册processors");
        // raft core processors
        final AppendEntriesRequestProcessor appendEntriesRequestProcessor = new AppendEntriesRequestProcessor(raftExecutor);
        rpcServer.registerConnectionClosedEventListener(appendEntriesRequestProcessor);
        rpcServer.registerProcessor(appendEntriesRequestProcessor);
        rpcServer.registerProcessor(new GetFileRequestProcessor(raftExecutor));
        rpcServer.registerProcessor(new InstallSnapshotRequestProcessor(raftExecutor));
        rpcServer.registerProcessor(new RequestVoteRequestProcessor(raftExecutor));
        rpcServer.registerProcessor(new PingRequestProcessor());
        rpcServer.registerProcessor(new TimeoutNowRequestProcessor(raftExecutor));
        rpcServer.registerProcessor(new ReadIndexRequestProcessor(raftExecutor));
        // raft cli service
        rpcServer.registerProcessor(new AddPeerRequestProcessor(cliExecutor));
        rpcServer.registerProcessor(new RemovePeerRequestProcessor(cliExecutor));
        rpcServer.registerProcessor(new ResetPeerRequestProcessor(cliExecutor));
        rpcServer.registerProcessor(new ChangePeersRequestProcessor(cliExecutor));
        rpcServer.registerProcessor(new GetLeaderRequestProcessor(cliExecutor));
        rpcServer.registerProcessor(new SnapshotRequestProcessor(cliExecutor));
        rpcServer.registerProcessor(new TransferLeaderRequestProcessor(cliExecutor));
        rpcServer.registerProcessor(new GetPeersRequestProcessor(cliExecutor));
        rpcServer.registerProcessor(new AddLearnersRequestProcessor(cliExecutor));
        rpcServer.registerProcessor(new RemoveLearnersRequestProcessor(cliExecutor));
        rpcServer.registerProcessor(new ResetLearnersRequestProcessor(cliExecutor));
    }

    /**
     * Creates a raft RPC server and starts it.
     *
     * @param endpoint server address to bind
     * @return a rpc server instance
     */
    public static RpcServer createAndStartRaftRpcServer(final Endpoint endpoint) {
        return createAndStartRaftRpcServer(endpoint, null, null);
    }

    /**
     * Creates a raft RPC server and starts it.
     *
     * @param endpoint     server address to bind
     * @param raftExecutor executor to handle RAFT requests.
     * @param cliExecutor  executor to handle CLI service requests.
     * @return a rpc server instance
     */
    public static RpcServer createAndStartRaftRpcServer(final Endpoint endpoint, final Executor raftExecutor,
                                                        final Executor cliExecutor) {
        final RpcServer server = createRaftRpcServer(endpoint, raftExecutor, cliExecutor);
        server.init(null);
        return server;
    }
}
