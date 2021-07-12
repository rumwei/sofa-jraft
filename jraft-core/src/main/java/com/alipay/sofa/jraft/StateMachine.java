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

import com.alipay.sofa.jraft.conf.Configuration;
import com.alipay.sofa.jraft.entity.LeaderChangeContext;
import com.alipay.sofa.jraft.error.RaftException;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotReader;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotWriter;

/**
 * 业务逻辑实现的主要接口，状态机运行在每个 raft 节点上，提交的 task 如果成功，最终都会复制应用到每个节点的状态机上。
 * |StateMachine| is the sink of all the events of a very raft node.
 * Implement a specific StateMachine for your own business logic.
 * NOTE: All the interfaces are not guaranteed to be thread safe and they are
 * called sequentially, saying that every single operation will block all the
 * following ones.
 *
 * 因StateMachine接口的方法比较多，并且大多数方法可能不需要做一些业务处理，因此jraft提供了一个
 * {@link com.alipay.sofa.jraft.core.StateMachineAdapter}桥接类，方便适配实现状态机，
 * 除了强制要实现 onApply 方法外，其他方法都提供了默认实现，也就是简单地打印日志，用户可以选择实现特定的方法
 */
public interface StateMachine {

    /**
     * 最核心的方法，应用{@link com.alipay.sofa.jraft.entity.Task}任务列表(通过|iterator|来遍历)到状态机,任务将按照提交顺序应用
     * <p>
     * 当传递给Node#apply(Task)的一个或多个任务被committed到raft group中(quorum of the group peers have received
     *      * those tasks and stored them on the backing storage)后，将会调用该onApply方法
     * <p>
     * 请注意，一旦该方法返回给了caller，我们就认为这一批任务都已经成功地应用到了状态机上，如果因错误、异常等导致存在应用失败的任务，
     * 则这会被当做一个critical级别的错误，报告给状态机的onError方法，错误类型为ERROR_TYPE_STATE_MACHINE
     * @param iter iterator of states 将要被应用到状态机上的状态
     */
    void onApply(final Iterator iter);

    /**
     * 当状态机所在 raft 节点被关闭的时候调用，可以用于一些状态机的资源清理工作，比如关闭文件等。
     * Default do nothing
     */
    void onShutdown();

    /**
     * 用户自定义快照产生方法，该方法将会阻塞StateMachine#onApply(Iterator)方法，以保证用户可以捕获当前状态机的状态
     * 当fsm支持cow(copy-on-write)时，用户可以以异步地的方式来产生快照
     *
     * 快照保存完成切记调用done.run(status)方法
     * Default: Save nothing and returns error.
     * @param writer snapshot writer
     * @param done   callback
     */
    void onSnapshotSave(final SnapshotWriter writer, final Closure done);

    /**
     * 用户自定义snapshot加载函数，将从SnapshotReader中读取出来的snapshot文件应用到当前状态机中
     *
     * 需要注意的是，程序启动会调用该方法，因此业务状态机在启动之前应该保持状态为空，如果状态机持久化了数据，则在启动之前应先清除数据
     * 然后依赖raft snapshot + reply raft log两步来恢复状态机数据
     *
     * Default: Load nothing and returns error.
     *
     * @param reader snapshot reader
     * @return true on success
     */
    boolean onSnapshotLoad(final SnapshotReader reader);

    /**
     * 当前状态机所属的raft节点成为leader时会调用该方法，默认do nothing
     * @param term 该leader所属任期
     */
    void onLeaderStart(final long term);

    /**
     * 当前状态机所属的raft节点失去leader资格时会调用该方法
     *
     * @param status 描述了失去leader资格的原因，比如主动转移 leadership、重新发生选举等
     */
    void onLeaderStop(final Status status);

    /**
     * 当发生critical错误时，会调用该方法，入参RaftException包含了status等详细的错误信息
     * 该方法被调用后，就不允许再修改该node的状态机了，直到错误被修复并重启该node
     * @param e raft error message
     */
    void onError(final RaftException e);

    /**
     *  当一个 raft group 的节点配置提交到 raft group 日志的时候调用，通常不需要实现此方法，或者打印个日志即可。
     * @param conf committed configuration
     */
    void onConfigurationCommitted(final Configuration conf);

    /**
     * This method is called when a follower stops following a leader and its leaderId becomes null,
     * situations including:
     * 1. handle election timeout and start preVote
     * 2. receive requests with higher term such as VoteRequest from a candidate
     * or appendEntries request from a new leader
     * 3. receive timeoutNow request from current leader and start request vote.
     * <p>
     * the parameter ctx gives the information(leaderId, term and status) about the
     * very leader whom the follower followed before.
     * User can reset the node's information as it stops following some leader.
     *
     * @param ctx context of leader change
     */
    void onStopFollowing(final LeaderChangeContext ctx);

    /**
     * 当一个follower或者candidate开始follow一个leader时调用。该方法调用前，node的leaderId应该为null，调用后，会被设置为leader的id
     * situations including:
     * 1. a candidate receives appendEntries request from a leader. Then the candidate become a follower
     * 2. a follower(without leader) receives first appendEntries from a leader
     * <p>
     * the parameter ctx gives the information(leaderId, term and status) about
     * the very leader whom the follower starts to follow.
     * User can reset the node's information as it starts to follow some leader.
     *
     * @param ctx context of leader change
     */
    void onStartFollowing(final LeaderChangeContext ctx);
}
