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
package com.alipay.sofa.jraft.util.timer;

import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 用的是Netty的实现
 * Schedules {@link TimerTask}s for one-time future execution in a background
 * thread.
 * <p>
 * Forked from <a href="https://github.com/netty/netty">Netty</a>.
 *
 * Timer、Timeout、TimerTask三者的关系
 * Timer管理着多个Timeout，一个Timeout会包含一个TimeTask
 */
public interface Timer {

    /**
     * 创建一个定时任务，要执行的逻辑在{@link TimerTask}中定义
     *
     * @return a handle which is associated with the specified task
     * @throws IllegalStateException      if this timer has been {@linkplain #stop() stopped} already
     * @throws RejectedExecutionException if the pending timeouts are too many and creating new timeout
     *                                    can cause instability in the system.
     */
    Timeout newTimeout(final TimerTask task, final long delay, final TimeUnit unit);

    /**
     * 停止所有还未被执行的定时任务，并释放被该持有的所有资源{@link Timer}
     * 会返回所有被取消的任务，Timeout中包含有Timer和TimerTask
     */
    Set<Timeout> stop();
}
