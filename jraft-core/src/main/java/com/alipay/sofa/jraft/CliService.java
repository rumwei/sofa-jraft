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
package com.alipay.sofa.jraft;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.alipay.sofa.jraft.conf.Configuration;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.option.CliOptions;

/**
 * Client command-line service
 * 该Service是Jraft在raft group节点提供的Rpc服务中暴露的一系列用于管理raft group的服务接口
 *
 * 简单使用示例：
 * // 创建并初始化 CliService
 * CliService cliService = RaftServiceFactory.createAndInitCliService(new CliOptions());
 * // 使用CliService
 * Configuration conf = JRaftUtils.getConfiguration("localhost:8081,localhost:8082,localhost:8083");
 * Status status = cliService.addPeer("jraft_group", conf, new PeerId("localhost", 8083));
 * if(status.isOk()){
 *    System.out.println("添加节点成功");
 * }
 */
public interface CliService extends Lifecycle<CliOptions> {

    //增加节点peer到groupId所对应的raft group中，其中peer包含配置conf
    Status addPeer(final String groupId, final Configuration conf, final PeerId peer);
    //删除groupId所对应的raft group中的节点peer，其中peer包含配置conf
    Status removePeer(final String groupId, final Configuration conf, final PeerId peer);
    //gracefully变更groupId所对应的raft group中的节点，将conf对应的旧节点迁移为newPeers对应的新节点
    Status changePeers(final String groupId, final Configuration conf, final Configuration newPeers);
    //重置groupId所对应的raft group中的peer节点的配置为新配置newPeers，仅仅在特殊情况下使用
    Status resetPeer(final String groupId, final PeerId peer, final Configuration newPeers);
    //learner的概念见note.md
    //将包含conf配置的新learners添加到groupId所对应的raft group中
    Status addLearners(final String groupId, final Configuration conf, final List<PeerId> learners);
    //将包含conf配置的learners从groupId所对应的raft group中移除
    Status removeLearners(final String groupId, final Configuration conf, final List<PeerId> learners);
    //将groupId所对应的raft group中的learner转换为follower，该follower的配置为conf
    Status learner2Follower(final String groupId, final Configuration conf, final PeerId learner);
    //更新groupId所对应的raft group中的learners节点信息，conf为当前raft group的配置
    Status resetLearners(final String groupId, final Configuration conf, final List<PeerId> learners);
    //让groupId所对应的raft group中的leader将leader角色转给peer，conf为当前集群配置
    Status transferLeader(final String groupId, final Configuration conf, final PeerId peer);
    //触发groupId所对应的raft group中的节点peer立即dump一次该节点的快照
    Status snapshot(final String groupId, final PeerId peer);
    //获取groupId所对应的raft group中的leader
    Status getLeader(final String groupId, final Configuration conf, final PeerId leaderId);
    //获取groupId所对应的raft group中的所有节点信息，conf为目标节点配置
    List<PeerId> getPeers(final String groupId, final Configuration conf);
    //获取groupId所对应的raft group中的所有存活节点信息，conf为目标节点配置
    List<PeerId> getAlivePeers(final String groupId, final Configuration conf);
    //获取groupId所对应的raft group中的所有learners节点信息，conf为目标节点配置
    List<PeerId> getLearners(final String groupId, final Configuration conf);
    //获取groupId所对应的raft group中的所有存活learners节点信息，conf为目标节点配置
    List<PeerId> getAliveLearners(final String groupId, final Configuration conf);
    /**
     * Balance the number of leaders.
     * @param balanceGroupIds   all raft group ids to balance
     * @param conf              configuration of all nodes
     * @param balancedLeaderIds the result of all balanced leader ids
     */
    Status rebalance(final Set<String> balanceGroupIds, final Configuration conf,
                     final Map<String, PeerId> balancedLeaderIds);
}
