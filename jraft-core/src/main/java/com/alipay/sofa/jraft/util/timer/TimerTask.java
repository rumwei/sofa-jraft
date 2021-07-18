/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.alipay.sofa.jraft.util.timer;

/**
 * A task which is executed after the delay specified with
 * Timer#newTimeout(TimerTask, long, TimeUnit).
 * <p>
 * Forked from <a href="https://github.com/netty/netty">Netty</a>.
 */
public interface TimerTask {

    /**
     * 会在某个特定的时间延时后执行，该延时时间通过Timer#newTimeout(TimerTask, long, TimeUnit)设置
     * @param timeout 与该执行任务相关的Timeout对象，实际上Timeout对象是包含TimerTask对象的，这跟平时的单向依赖编程习惯不太一样
     *                好处是在延时任务执行过程中，我们可以利用timeout来做点其他事情
     */
    void run(final Timeout timeout) throws Exception;
}
