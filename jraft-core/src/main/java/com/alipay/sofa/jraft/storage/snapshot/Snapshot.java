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
package com.alipay.sofa.jraft.storage.snapshot;

import java.util.Set;

import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.Status;
import com.google.protobuf.Message;

/**
 * 表示一个状态机快照
 * 快照要解决的问题：
 * 当一个Raft节点需要重启时，内存中的状态机的状态将会丢失，在启动过程中将重放日志存储中的所有日志，重建整个状态机实例，但这会有如下3个问题：
 * 1.如果任务提交比较频繁，比如消息中间件这个场景，那么会导致整个重建过程很长，启动缓慢
 * 2.如果日志很多，节点需要存储所有的日志，这对存储是一个资源占用，不可持续
 * 3.如果增加一个节点，新节点需要从leader获取所有的日志重放到状态机，这对leader和网络带宽都是不小的负担
 *
 * 为了解决上述问题，才引入了snapshot，所谓snapshot就是为状态机当前的最新状态打一个"镜像"单独保存，之前包含在这个镜像中的日志就可以删除了，
 * 从而减少日志存储的占用。同时重启之后可以直接加载最新的snapshot镜像，然后重放在此之后的日志即可，从而加快启动过程。最后，如果有新节点加入，
 * 可以先从leader拷贝最新的snapshot安装到本地状态机，然后从leader同步后续的日志，从而减轻leader和网络带宽负担，同时加快跟上raft group
 * 的速度
 *
 * 使用：
 * 自动：启用snapshot需要设置NodeOptions的snapshotUri属性，指定snapshot的存储路径。之后默认会启动一个定时器自动做snapshot，时间间隔
 * 通过NodeOptions.snapshotIntervalSecs属性指定，默认3600s
 * 手动：用户也可以手动触发snapshot构建，通过{@link com.alipay.sofa.jraft.Node#snapshot(Closure)}接口
 *
 * 对应的状态机保存snapshot或者加载snapshot到状态机，分别对应如下两个方法
 * {@link com.alipay.sofa.jraft.StateMachine#onSnapshotSave(SnapshotWriter, Closure)}
 * {@link com.alipay.sofa.jraft.StateMachine#onSnapshotLoad(SnapshotReader)}
 */
public abstract class Snapshot extends Status {

    /**
     * Snapshot metadata file name.
     */
    public static final String JRAFT_SNAPSHOT_META_FILE = "__raft_snapshot_meta";
    /**
     * Snapshot file prefix.
     */
    public static final String JRAFT_SNAPSHOT_PREFIX = "snapshot_";
    /**
     * Snapshot uri scheme for remote peer
     */
    public static final String REMOTE_SNAPSHOT_URI_SCHEME = "remote://";

    /**
     * Get the path of the Snapshot
     */
    public abstract String getPath();

    /**
     * List all the existing files in the Snapshot currently
     */
    public abstract Set<String> listFiles();

    /**
     * Get file meta by fileName.
     */
    public abstract Message getFileMeta(final String fileName);
}
