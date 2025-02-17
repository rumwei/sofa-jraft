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

import com.alipay.sofa.jraft.closure.ReadIndexClosure;
import com.alipay.sofa.jraft.conf.Configuration;
import com.alipay.sofa.jraft.core.NodeMetrics;
import com.alipay.sofa.jraft.core.Replicator;
import com.alipay.sofa.jraft.core.State;
import com.alipay.sofa.jraft.entity.NodeId;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.entity.Task;
import com.alipay.sofa.jraft.entity.UserLog;
import com.alipay.sofa.jraft.error.LogIndexOutOfBoundsException;
import com.alipay.sofa.jraft.error.LogNotFoundException;
import com.alipay.sofa.jraft.option.NodeOptions;
import com.alipay.sofa.jraft.option.RaftOptions;
import com.alipay.sofa.jraft.util.Describer;

/**
 * 表示一个 raft 节点，可以提交 task，以及查询 raft group 信息，比如当前状态、当前 leader/term 等
 * 该节点角色为leader、follower以及candidate之一，随着选举过程而转变
 *
 * 与PeerId的区别就是，PeerId是简化版的，PeerId参与构成Node
 *
 * 创建一个Raft Node节点可以通过{@link RaftServiceFactory#createAndInitRaftNode(String groupId, PeerId serverId, NodeOptions opts)}静态方法来进行
 *
 * 单纯一个Raft Node节点是没啥用的，需要多个节点来构成一个raft group，节点之间的通讯使用bolt框架或grpc框架的rpc服务，因此在创建节点后，
 * 需要将Raft Node加入到{@link NodeManager}中
 *
 * 节点提供给客户端的服务可以分为两类：
 * 1.读服务，可以从leader或者follower读取状态机数据，但是follower读取的数据不保证最新，存在时间差，除非启用线性一致读。
 * 2.写服务，更改状态机数据，只能提交到leader写入
 */
public interface Node extends Lifecycle<NodeOptions>, Describer {

    //region 核心方法
    /**
     * [线程安全且非阻塞]
     * <p>
     * 提交一个新任务到raft group
     * <p>
     * About the ownership:
     * |task.data|: 为了更高的性能，本方法会直接操作任务数据data，如果想留存处理前的数据，则需要在调用该方法前保存数据的副本
     * |task.done|: 如果数据成功提交到了raft group，我们将传递该回调task.done给 #{@link StateMachine#onApply(Iterator)}.
     * 否则会设置对应的错误信息，然后传递该回调task.done给 #{@link StateMachine#onApply(Iterator)}.
     * @param task task to apply
     */
    void apply(final Task task);

    PeerId getLeaderId(); //获取当前raft group的leader peerId，如果未知，返回null

    /**
     * 停止当前raft节点(replica node)
     * @param done 停止后的回调逻辑
     */
    void shutdown(final Closure done);

    /**
     * Block the thread until the node is successfully stopped.
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    void join() throws InterruptedException;

    /**
     * 触发当前节点执行一次snapshot保存操作，如果可能会立即执行。当snapshot保存完成后，会调用done回调逻辑，来获取保存snapshot的细节结果信息
     * @param done callback
     */
    void snapshot(final Closure done);
    //endregion

    /**
     * Get current node id.
     */
    NodeId getNodeId();

    /**
     * Get the node metrics, only valid when node option {@link NodeOptions#isEnableMetrics()} is true.
     */
    NodeMetrics getNodeMetrics();

    /**
     * Get the raft group id.
     */
    String getGroupId();

    /**
     * Get the node options.
     */
    NodeOptions getOptions();

    /**
     * Get the raft options
     */
    RaftOptions getRaftOptions();

    /**
     * Returns true when the node is leader.
     */
    boolean isLeader();

    /**
     * Returns true when the node is leader.
     *
     * @param blocking if true, will be blocked until the node finish it's state change
     */
    boolean isLeader(final boolean blocking);

    /**
     * [Thread-safe and wait-free]
     * <p>
     * Starts a linearizable read-only query request with request context(optional,
     * such as request id etc.) and closure.  The closure will be called when the
     * request is completed, and user can read data from state machine if the result
     * status is OK.
     *
     * @param requestContext the context of request
     * @param done           callback
     * @since 0.0.3
     */
    void readIndex(final byte[] requestContext, final ReadIndexClosure done);

    /**
     * List peers of this raft group, only leader returns.
     * <p>
     * [NOTE] <strong>when list_peers concurrency with {@link #addPeer(PeerId, Closure)}/{@link #removePeer(PeerId, Closure)},
     * maybe return peers is staled.  Because {@link #addPeer(PeerId, Closure)}/{@link #removePeer(PeerId, Closure)}
     * immediately modify configuration in memory</strong>
     *
     * @return the peer list
     */
    List<PeerId> listPeers();

    /**
     * List all alive peers of this raft group, only leader returns.</p>
     * <p>
     * [NOTE] <strong>list_alive_peers is just a transient data (snapshot)
     * and a short-term loss of response by the follower will cause it to
     * temporarily not exist in this list.</strong>
     *
     * @return the alive peer list
     * @since 1.2.6
     */
    List<PeerId> listAlivePeers();

    /**
     * List all learners of this raft group, only leader returns.</p>
     * <p>
     * [NOTE] <strong>when listLearners concurrency with {@link #addLearners(List, Closure)}/{@link #removeLearners(List, Closure)}/{@link #resetLearners(List, Closure)},
     * maybe return peers is staled.  Because {@link #addLearners(List, Closure)}/{@link #removeLearners(List, Closure)}/{@link #resetLearners(List, Closure)}
     * immediately modify configuration in memory</strong>
     *
     * @return the learners set
     * @since 1.3.0
     */
    List<PeerId> listLearners();

    /**
     * List all alive learners of this raft group, only leader returns.</p>
     * <p>
     * [NOTE] <strong>when listAliveLearners concurrency with {@link #addLearners(List, Closure)}/{@link #removeLearners(List, Closure)}/{@link #resetLearners(List, Closure)},
     * maybe return peers is staled.  Because {@link #addLearners(List, Closure)}/{@link #removeLearners(List, Closure)}/{@link #resetLearners(List, Closure)}
     * immediately modify configuration in memory</strong>
     *
     * @return the  alive learners set
     * @since 1.3.0
     */
    List<PeerId> listAliveLearners();

    /**
     * Add a new peer to the raft group. done.run() would be invoked after this
     * operation finishes, describing the detailed result.
     *
     * @param peer peer to add
     * @param done callback
     */
    void addPeer(final PeerId peer, final Closure done);

    /**
     * Remove the peer from the raft group. done.run() would be invoked after
     * operation finishes, describing the detailed result.
     *
     * @param peer peer to remove
     * @param done callback
     */
    void removePeer(final PeerId peer, final Closure done);

    /**
     * Change the configuration of the raft group to |newPeers| , done.un()
     * would be invoked after this operation finishes, describing the detailed result.
     *
     * @param newPeers new peers to change
     * @param done     callback
     */
    void changePeers(final Configuration newPeers, final Closure done);

    /**
     * Reset the configuration of this node individually, without any replication
     * to other peers before this node becomes the leader. This function is
     * supposed to be invoked when the majority of the replication group are
     * dead and you'd like to revive the service in the consideration of
     * availability.
     * Notice that neither consistency nor consensus are guaranteed in this
     * case, BE CAREFULE when dealing with this method.
     *
     * @param newPeers new peers
     */
    Status resetPeers(final Configuration newPeers);

    /**
     * Add some new learners to the raft group. done.run() will be invoked after this
     * operation finishes, describing the detailed result.
     *
     * @param learners learners to add
     * @param done     callback
     * @since 1.3.0
     */
    void addLearners(final List<PeerId> learners, final Closure done);

    /**
     * Remove some learners from the raft group. done.run() will be invoked after this
     * operation finishes, describing the detailed result.
     *
     * @param learners learners to remove
     * @param done     callback
     * @since 1.3.0
     */
    void removeLearners(final List<PeerId> learners, final Closure done);

    /**
     * Reset learners in the raft group. done.run() will be invoked after this
     * operation finishes, describing the detailed result.
     *
     * @param learners learners to set
     * @param done     callback
     * @since 1.3.0
     */
    void resetLearners(final List<PeerId> learners, final Closure done);

    /**
     * Reset the election_timeout for the every node.
     *
     * @param electionTimeoutMs the timeout millis of election
     */
    void resetElectionTimeoutMs(final int electionTimeoutMs);

    /**
     * Try transferring leadership to |peer|. If peer is ANY_PEER, a proper follower
     * will be chosen as the leader for the next term.
     * Returns 0 on success, -1 otherwise.
     *
     * @param peer the target peer of new leader
     * @return operation status
     */
    Status transferLeadershipTo(final PeerId peer);

    /**
     * Read the first committed user log from the given index.
     * Return OK on success and user_log is assigned with the very data. Be awared
     * that the user_log may be not the exact log at the given index, but the
     * first available user log from the given index to lastCommittedIndex.
     * Otherwise, appropriate errors are returned:
     * - return ELOGDELETED when the log has been deleted;
     * - return ENOMOREUSERLOG when we can't get a user log even reaching lastCommittedIndex.
     * [NOTE] in consideration of safety, we use lastAppliedIndex instead of lastCommittedIndex
     * in code implementation.
     *
     * @param index log index
     * @return user log entry
     * @throws LogNotFoundException         the user log is deleted at index.
     * @throws LogIndexOutOfBoundsException the special index is out of bounds.
     */
    UserLog readCommittedUserLog(final long index);

    /**
     * SOFAJRaft users can implement the ReplicatorStateListener interface by themselves.
     * So users can do their own logical operator in this listener when replicator created, destroyed or had some errors.
     *
     * @param replicatorStateListener added ReplicatorStateListener which is implemented by users.
     */
    void addReplicatorStateListener(final Replicator.ReplicatorStateListener replicatorStateListener);

    /**
     * End User can remove their implement the ReplicatorStateListener interface by themselves.
     *
     * @param replicatorStateListener need to remove the ReplicatorStateListener which has been added by users.
     */
    void removeReplicatorStateListener(final Replicator.ReplicatorStateListener replicatorStateListener);

    /**
     * Remove all the ReplicatorStateListeners which have been added by users.
     */
    void clearReplicatorStateListeners();

    /**
     * Get the ReplicatorStateListeners which have been added by users.
     *
     * @return node's replicatorStatueListeners which have been added by users.
     */
    List<Replicator.ReplicatorStateListener> getReplicatorStatueListeners();

    /**
     * Get the node's target election priority value.
     *
     * @return node's target election priority value.
     * @since 1.3.0
     */
    int getNodeTargetPriority();

    /**
     * Get the node's state.
     *
     * @return node's state.
     * @since 1.3.8
     */
    State getNodeState();
}
