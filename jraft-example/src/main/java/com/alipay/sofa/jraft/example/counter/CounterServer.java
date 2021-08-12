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
package com.alipay.sofa.jraft.example.counter;

import java.io.File;
import java.io.IOException;

import com.alipay.sofa.jraft.util.SlsLogUtil;
import com.aliyun.openservices.log.util.JsonUtils;
import org.apache.commons.io.FileUtils;

import com.alipay.sofa.jraft.Node;
import com.alipay.sofa.jraft.RaftGroupService;
import com.alipay.sofa.jraft.conf.Configuration;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.example.counter.rpc.GetValueRequestProcessor;
import com.alipay.sofa.jraft.example.counter.rpc.IncrementAndGetRequestProcessor;
import com.alipay.sofa.jraft.example.counter.rpc.ValueResponse;
import com.alipay.sofa.jraft.option.NodeOptions;
import com.alipay.sofa.jraft.rpc.RaftRpcServerFactory;
import com.alipay.sofa.jraft.rpc.RpcServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Counter server that keeps a counter value in a raft group.
 *
 * @author boyan (boyan@alibaba-inc.com)
 * <p>
 * 2018-Apr-09 4:51:02 PM
 */
public class CounterServer {

    private static final Logger log = LoggerFactory.getLogger(CounterServer.class);

    private RaftGroupService raftGroupService;
    private Node node;
    private CounterStateMachine fsm;

    /**
     * @param dataPath 日志、元数据等保存地址
     * @param groupId groupId
     * @param serverId 当前节点信息，包括地址、端口等
     * @param nodeOptions 集群配置，包括集群所有节点信息、选举超时时间等
     * @throws IOException
     */
    public CounterServer(final String dataPath, final String groupId, final PeerId serverId,
                         final NodeOptions nodeOptions) throws IOException {
        SlsLogUtil.info("CounterServer init", "traceId", "CounterServer初始化构造函数入参:\n"
        + "dataPath: " + dataPath + "\n"
        + "groupId:" + groupId + "\n"
        + "serverId:" + JsonUtils.serialize(serverId) + "\n"
        + "nodeOptions: 集群配置，详见：https://www.jianshu.com/p/b19a682c307a \n"
        );
        // 初始化路径
        FileUtils.forceMkdir(new File(dataPath));

        // 这里让 raft RPC 和业务 RPC 使用同一个 RPC server, 通常也可以分开
        final RpcServer rpcServer = RaftRpcServerFactory.createRaftRpcServer(serverId.getEndpoint());
        // 注册业务处理器
        CounterService counterService = new CounterServiceImpl(this);
        rpcServer.registerProcessor(new GetValueRequestProcessor(counterService));
        rpcServer.registerProcessor(new IncrementAndGetRequestProcessor(counterService));
        // 初始化状态机
        this.fsm = new CounterStateMachine();
        // 设置状态机到启动参数
        nodeOptions.setFsm(this.fsm);
        // 设置存储路径
        // 日志, 必须
        nodeOptions.setLogUri(dataPath + File.separator + "log");
        // 元信息, 必须
        nodeOptions.setRaftMetaUri(dataPath + File.separator + "raft_meta");
        // snapshot, 可选, 一般都推荐
        nodeOptions.setSnapshotUri(dataPath + File.separator + "snapshot");
        // 初始化 raft group 服务框架
        this.raftGroupService = new RaftGroupService(groupId, serverId, nodeOptions, rpcServer);
        // 启动
        this.node = this.raftGroupService.start();
    }

    public CounterStateMachine getFsm() {
        return this.fsm;
    }

    public Node getNode() {
        return this.node;
    }

    public RaftGroupService RaftGroupService() {
        return this.raftGroupService;
    }

    /**
     * Redirect request to new leader
     */
    public ValueResponse redirect() {
        final ValueResponse response = new ValueResponse();
        response.setSuccess(false);
        if (this.node != null) {
            final PeerId leader = this.node.getLeaderId();
            if (leader != null) {
                response.setRedirect(leader.toString());
            }
        }
        return response;
    }

    public static void main(final String[] args) throws IOException {
        SlsLogUtil.info("topic", "traceId", "start Counter Server");
        if (args.length != 4) {
            //使用规则
            log.info("Usage : java com.alipay.sofa.jraft.example.counter.CounterServer {dataPath} {groupId} {serverId} {initConf}");
            //示例
            log.info("Example: java com.alipay.sofa.jraft.example.counter.CounterServer /tmp/server1 counter 127.0.0.1:8081 127.0.0.1:8081,127.0.0.1:8082,127.0.0.1:8083");
            System.exit(1);
        }
        final String dataPath = args[0];
        final String groupId = args[1];
        final String serverIdStr = args[2];
        final String initConfStr = args[3];

        final NodeOptions nodeOptions = new NodeOptions();
        // 为了测试,调整 snapshot 间隔等参数
        // 设置选举超时时间为 1 秒
        nodeOptions.setElectionTimeoutMs(1000);
        // 关闭 CLI 服务。
        nodeOptions.setDisableCli(false);
        // 每隔30秒做一次 snapshot
        nodeOptions.setSnapshotIntervalSecs(30);
        // 利用入参serverIdStr来填充serverId，如serverIdStr=127:0:0:1:8081
        final PeerId serverId = new PeerId();
        if (!serverId.parse(serverIdStr)) {
            throw new IllegalArgumentException("Fail to parse serverId:" + serverIdStr);
        }
        //利用入参initConfStr来填充initConf，如initConfStr=127.0.0.1:8081,127.0.0.1:8082,127.0.0.1:8083，最终填充nodeOptions.initialConf字段
        final Configuration initConf = new Configuration();
        if (!initConf.parse(initConfStr)) {
            throw new IllegalArgumentException("Fail to parse initConf:" + initConfStr);
        }
        // 设置初始集群配置
        nodeOptions.setInitialConf(initConf);

        // 启动
        final CounterServer counterServer = new CounterServer(dataPath, groupId, serverId, nodeOptions);
        System.out.println("Started counter server at port:"
                + counterServer.getNode().getNodeId().getPeerId().getPort());
        SlsLogUtil.info("CountStart", "","Started counter server at port:"
                + counterServer.getNode().getNodeId().getPeerId().getPort());
    }
}
