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

import java.util.Collections;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <h3>Implementation Details</h3>
 * <p>
 * {@link HashedWheelTimer} is based on
 * <a href="http://cseweb.ucsd.edu/users/varghese/">George Varghese</a> and
 * Tony Lauck's paper,
 * <a href="http://cseweb.ucsd.edu/users/varghese/PAPERS/twheel.ps.Z">'Hashed
 * and Hierarchical Timing Wheels: data structures to efficiently implement a
 * timer facility'</a>.  More comprehensive slides are located
 * <a href="http://www.cse.wustl.edu/~cdgill/courses/cs6874/TimingWheels.ppt">here</a>.
 * <p>
 * <p>
 * Forked from <a href="https://github.com/netty/netty">Netty</a>.
 * 时间轮实现延时任务类
 *
 * 工作线程是单线程，即所有的延时任务都是一个线程来完成的。即假设A任务执行完需要3000ms
 * 1.设置A任务在t=1000ms时执行，B任务在t=5000ms时执行，则线程执行完A任务，会进入休眠，1000ms之后，再执行B任务
 * 2.设置A任务在t=1000ms时执行，B任务在t=2000ms时执行，则线程执行完A任务，t已经是4000ms了，此时会直接执行B任务，即B任务的实际执行时间要晚于设置的时间
 * 默认最小单位是100ms，可通过构造函数指定。延时任务的时间间隔不能小于该最小单位，如果小于，则无法区分。
 * 如设置A任务在t=510ms执行，B任务在t=530ms执行，则工作线程会在t=500ms开始执行A任务，执行完接着执行B任务
 *
 * 默认的时间轮长度为512，即一圈的时间是100ms*512=51200ms，对应的数据结构即为下方的{@link #wheel}成员变量，它是一个数组，数组元素是一个链表
 * 链表成员即为Timeout变量
 *
 * 时间轮中涉及两类线程
 * 1.提交任务的线程，这类线程即为需要延时调度的各个业务线程，该线程不在时间轮的管理范围中，时间轮实现中只负责提供提交任务的代码。且该线程只负责将
 *   任务加入到队列中，并不负责根据延时时间来将任务分配到时间轮中。
 * 2.执行任务的线程(下称工作线程)，该线程为时间轮提供并维护，且是单线程  -- 如何维持单线程的持续执行？？
 *
 * 时间轮执行过程
 * 1.工作线程到达每个时间整点的时候开始工作。在时间轮中，时间都是相对时间，工作线程第一次启动时为t=0，之后每次tick的时间即为此处说的时间整点
 * 2.首先工作线程会检查延时队列{@link #timeouts}中是否有由提交任务线程新提交的任务，如果有，则取出这些任务
 * 3.根据每个任务所指定的延迟时间，将其分配到{@link #wheel}的相应位置上，如t=230ms、t=240ms、t=512*100*n+250ms-n为整数三个任务就会在同
 *   一个位置，并构成一个链表
 * 4.步骤3中的分配完成后，才真正的开始执行本tick中的链表对应的任务
 * 5.判断每个任务的轮次，如果轮次为0，则从链表中移除任务，并直接执行，如果轮次大于0，则将轮次-1即可
 */
public class HashedWheelTimer implements Timer {

    private static final Logger LOG = LoggerFactory
            .getLogger(HashedWheelTimer.class);

    private static final int INSTANCE_COUNT_LIMIT = 256;
    private static final AtomicInteger instanceCounter = new AtomicInteger();
    private static final AtomicBoolean warnedTooManyInstances = new AtomicBoolean();

    private static final AtomicIntegerFieldUpdater<HashedWheelTimer> workerStateUpdater = AtomicIntegerFieldUpdater
            .newUpdater(
                    HashedWheelTimer.class,
                    "workerState");

    private final Worker worker = new Worker();
    private final Thread workerThread;

    public static final int WORKER_STATE_INIT = 0;
    public static final int WORKER_STATE_STARTED = 1;
    public static final int WORKER_STATE_SHUTDOWN = 2;
    @SuppressWarnings({"unused", "FieldMayBeFinal"})
    private volatile int workerState; // 0 - init, 1 - started, 2 - shut down

    private final long tickDuration;
    private final HashedWheelBucket[] wheel;
    private final int mask;
    private final CountDownLatch startTimeInitialized = new CountDownLatch(1);
    private final Queue<HashedWheelTimeout> timeouts = new ConcurrentLinkedQueue<>();
    private final Queue<HashedWheelTimeout> cancelledTimeouts = new ConcurrentLinkedQueue<>();
    private final AtomicLong pendingTimeouts = new AtomicLong(0);
    private final long maxPendingTimeouts;

    private volatile long startTime;

    //region 构造函数
    public HashedWheelTimer() {
        this(Executors.defaultThreadFactory());
    }
    public HashedWheelTimer(ThreadFactory threadFactory) {
        this(threadFactory, 100, TimeUnit.MILLISECONDS);
    }
    public HashedWheelTimer(long tickDuration, TimeUnit unit) {
        this(Executors.defaultThreadFactory(), tickDuration, unit);
    }
    public HashedWheelTimer(ThreadFactory threadFactory, long tickDuration, TimeUnit unit) {
        this(threadFactory, tickDuration, unit, 512);
    }
    public HashedWheelTimer(long tickDuration, TimeUnit unit, int ticksPerWheel) {
        this(Executors.defaultThreadFactory(), tickDuration, unit, ticksPerWheel);
    }
    public HashedWheelTimer(ThreadFactory threadFactory, long tickDuration, TimeUnit unit, int ticksPerWheel) {
        this(threadFactory, tickDuration, unit, ticksPerWheel, -1);
    }
    /**
     * 创建一个新的Timer
     * @param threadFactory 线程工厂，负责创建一个后台线程，负责延时任务的执行。即上面说的工作线程
     * @param tickDuration 两次tick之间的时间间隔，默认100
     * @param unit 上tickDuration的时间单位，默认ms
     * @param ticksPerWheel 时间轮长度，默认512
     * @param maxPendingTimeouts 最大允许等待的timeout数目，如果未执行任务数达到该数目，继续通过newTimeout提交任务会返回
     *                           {@link RejectedExecutionException}异常。如果设置为0或负数，则表示不限制提交的任务数目。
     * @throws NullPointerException     如果{@code threadFactory}或{@code unit}为null时抛出
     * @throws IllegalArgumentException 如果{@code tickDuration}或{@code ticksPerWheel}为非正数
     */
    public HashedWheelTimer(ThreadFactory threadFactory, long tickDuration, TimeUnit unit, int ticksPerWheel, long maxPendingTimeouts) {
        //region 参数校验
        if (threadFactory == null) { throw new NullPointerException("threadFactory"); }
        if (unit == null) { throw new NullPointerException("unit"); }
        if (tickDuration <= 0) { throw new IllegalArgumentException("tickDuration must be greater than 0: " + tickDuration); }
        if (ticksPerWheel <= 0) { throw new IllegalArgumentException("ticksPerWheel must be greater than 0: " + ticksPerWheel); }
        //endregion

        //规整化ticksPerWheel，此处做了向上"取整"，保持数组长度为2的n次方
        wheel = createWheel(ticksPerWheel);
        mask = wheel.length - 1; //掩码，用来做取模

        this.tickDuration = unit.toNanos(tickDuration); //转换为ns

        //防止溢出
        if (this.tickDuration >= Long.MAX_VALUE / wheel.length) {
            throw new IllegalArgumentException(String.format(
                    "tickDuration: %d (expected: 0 < tickDuration in nanos < %d", tickDuration, Long.MAX_VALUE
                            / wheel.length));
        }
        //创建线程，此处只是做初始化，并没有启动，在后续第一次提交任务时，会启动该工作线程
        workerThread = threadFactory.newThread(worker);
        //设置最大允许的等待任务数
        this.maxPendingTimeouts = maxPendingTimeouts;
        //如果超过 INSTANCE_COUNT_LIMIT=256 个HashedWheelTimer 实例，会打印错误日志做提醒
        if (instanceCounter.incrementAndGet() > INSTANCE_COUNT_LIMIT
                && warnedTooManyInstances.compareAndSet(false, true)) {
            reportTooManyInstances();
        }
    }
    //endregion


    @Override
    protected void finalize() throws Throwable {
        try {
            super.finalize();
        } finally {
            // This object is going to be GCed and it is assumed the ship has sailed to do a proper shutdown. If
            // we have not yet shutdown then we want to make sure we decrement the active instance count.
            if (workerStateUpdater.getAndSet(this, WORKER_STATE_SHUTDOWN) != WORKER_STATE_SHUTDOWN) {
                instanceCounter.decrementAndGet();
            }
        }
    }

    private static HashedWheelBucket[] createWheel(int ticksPerWheel) {
        if (ticksPerWheel <= 0) {
            throw new IllegalArgumentException("ticksPerWheel must be greater than 0: " + ticksPerWheel);
        }
        if (ticksPerWheel > 1073741824) {
            throw new IllegalArgumentException("ticksPerWheel may not be greater than 2^30: " + ticksPerWheel);
        }

        ticksPerWheel = normalizeTicksPerWheel(ticksPerWheel);
        HashedWheelBucket[] wheel = new HashedWheelBucket[ticksPerWheel];
        for (int i = 0; i < wheel.length; i++) {
            wheel[i] = new HashedWheelBucket();
        }
        return wheel;
    }

    private static int normalizeTicksPerWheel(int ticksPerWheel) {
        int normalizedTicksPerWheel = 1;
        while (normalizedTicksPerWheel < ticksPerWheel) {
            normalizedTicksPerWheel <<= 1;
        }
        return normalizedTicksPerWheel;
    }

    /**
     * Starts the background thread explicitly.  The background thread will
     * start automatically on demand even if you did not call this method.
     *
     * @throws IllegalStateException if this timer has been
     *                               {@linkplain #stop() stopped} already
     */
    public void start() {
        switch (workerStateUpdater.get(this)) {
            case WORKER_STATE_INIT:
                if (workerStateUpdater.compareAndSet(this, WORKER_STATE_INIT, WORKER_STATE_STARTED)) {
                    workerThread.start();
                }
                break;
            case WORKER_STATE_STARTED:
                break;
            case WORKER_STATE_SHUTDOWN:
                throw new IllegalStateException("cannot be started once stopped");
            default:
                throw new Error("Invalid WorkerState");
        }

        // Wait until the startTime is initialized by the worker.
        while (startTime == 0) {
            try {
                /**
                 *  第一次提交任务的线程在调用本start()方法之后，需要使用startTime来计算所提交任务的相对时间，因此在本方法中利用CountDownLatch
                 *  来保证startTime初始化完成，才返回该方法，这样在该方法后续使用startTime就保证是初始化过了的，详细见{@link Worker#run()}
                 */
                startTimeInitialized.await();
            } catch (InterruptedException ignore) {
                // Ignore - it will be ready very soon.
            }
        }
    }

    @Override
    public Set<Timeout> stop() {
        if (Thread.currentThread() == workerThread) {
            throw new IllegalStateException(HashedWheelTimer.class.getSimpleName() + ".stop() cannot be called from "
                    + TimerTask.class.getSimpleName());
        }

        if (!workerStateUpdater.compareAndSet(this, WORKER_STATE_STARTED, WORKER_STATE_SHUTDOWN)) {
            // workerState can be 0 or 2 at this moment - let it always be 2.
            if (workerStateUpdater.getAndSet(this, WORKER_STATE_SHUTDOWN) != WORKER_STATE_SHUTDOWN) {
                instanceCounter.decrementAndGet();
            }

            return Collections.emptySet();
        }

        try {
            boolean interrupted = false;
            while (workerThread.isAlive()) {
                workerThread.interrupt();
                try {
                    workerThread.join(100);
                } catch (InterruptedException ignored) {
                    interrupted = true;
                }
            }

            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        } finally {
            instanceCounter.decrementAndGet();
        }
        return worker.unprocessedTimeouts();
    }

    @Override
    public Timeout newTimeout(TimerTask task, long delay, TimeUnit unit) {
        if (task == null) { throw new NullPointerException("task"); }
        if (unit == null) { throw new NullPointerException("unit"); }

        long pendingTimeoutsCount = pendingTimeouts.incrementAndGet();
        //判断如果设置了最大允许等待任务数，且已经达到阈值，则直接抛出RejectedExecutionException异常
        if (maxPendingTimeouts > 0 && pendingTimeoutsCount > maxPendingTimeouts) {
            pendingTimeouts.decrementAndGet();
            throw new RejectedExecutionException("Number of pending timeouts (" + pendingTimeoutsCount
                    + ") is greater than or equal to maximum allowed pending "
                    + "timeouts (" + maxPendingTimeouts + ")");
        }
        /**
         * 如果工作线程workThread没有启动，此处会负责启动
         * 在构造函数中可以看的workThread初始化传入的是work对象封装的任务，因此工作线程的执行逻辑定义在{@link Worker#run()}中
         */
        start();

        //将timeout添加到timeout队列中，它会在下一个tick中，由工作线程分配到时间轮的具体位置上，即会放到HashedWheelBucket数组元素的链表上
        long deadline = System.nanoTime() + unit.toNanos(delay) - startTime; //deadline为相对时间，相对于HashedWheelTimer的启动时间
        if (delay > 0 && deadline < 0) { //防止溢出
            deadline = Long.MAX_VALUE;
        }
        HashedWheelTimeout timeout = new HashedWheelTimeout(this, task, deadline);
        timeouts.add(timeout);
        return timeout; //返回封装了task，delay信息的timeout对象
    }

    /**
     * Returns the number of pending timeouts of this {@link Timer}.
     */
    public long pendingTimeouts() {
        return pendingTimeouts.get();
    }

    private static void reportTooManyInstances() {
        String resourceType = HashedWheelTimer.class.getSimpleName();
        LOG.error("You are creating too many {} instances.  {} is a shared resource that must be "
                + "reused across the JVM, so that only a few instances are created.", resourceType, resourceType);
    }

    private final class Worker implements Runnable {
        private final Set<Timeout> unprocessedTimeouts = new HashSet<>();
        private long tick; //tick过的次数，前面说过，时针每100ms tick一次

        @Override
        public void run() {
            //在HashedWheelTimer中，用的都是相对时间，因此需要启动时间作为基准，并且用volatile进行修饰
            startTime = System.nanoTime();
            if (startTime == 0) {
                //我们使用0作为startTime没有被初始化的标志，但此处我们已经做了初始化，因此如果初始化结果是0，为了和未初始化做区分，给赋值成1，
                //1ns的延迟对几乎所有的系统都不会有影响
                startTime = 1;
            }
            /**
             * 第一个提交任务的线程正await等待startTime初始化完成呢，唤醒它. 见{@link HashedWheelTimer#start()}
             */
            startTimeInitialized.countDown();
            //开始处理时间轮上的延时任务
            do {
                final long deadline = waitForNextTick();
                if (deadline > 0) {
                    int idx = (int) (tick & mask); //该次tick，bucket数组对应的index
                    processCancelledTasks(); //将cancelledTimeouts队列中记录的任务从timeout队列中移除
                    HashedWheelBucket bucket = wheel[idx];
                    transferTimeoutsToBuckets(); //将timeout队列中的任务填充到时间轮的buckets中
                    bucket.expireTimeouts(deadline); //处理此次tick对应的buckets数组中的相应bucket任务链表
                    tick++;
                }
            } while (workerStateUpdater.get(HashedWheelTimer.this) == WORKER_STATE_STARTED);
            /* 到这里，说明这个timer要关闭了(调用了timer.stop()来结束时间轮调度)，做一些清理工作 */
            //将所有bucket中还没来得及执行的timeout添加到unprocessedTimeouts这个HashSet中，最后作为stop()方法的返回，以告知用户停止时间轮后
            //还有这些timeout没有执行
            for (HashedWheelBucket bucket : wheel) {
                bucket.clearTimeouts(unprocessedTimeouts);
            }
            //此时任务队列中的任务肯定是没被执行的，也需要加到unprocessedTimeouts中
            for (; ; ) {
                HashedWheelTimeout timeout = timeouts.poll();
                if (timeout == null) {
                    break;
                }
                if (!timeout.isCancelled()) {
                    unprocessedTimeouts.add(timeout);
                }
            }
            processCancelledTasks();
        }

        private void transferTimeoutsToBuckets() {
            // transfer only max. 100000 timeouts per tick to prevent a thread to stale the workerThread when it just
            // adds new timeouts in a loop.
            for (int i = 0; i < 100000; i++) {
                HashedWheelTimeout timeout = timeouts.poll();
                if (timeout == null) {
                    // all processed
                    break;
                }
                if (timeout.state() == HashedWheelTimeout.ST_CANCELLED) {
                    // Was cancelled in the meantime.
                    continue;
                }

                long calculated = timeout.deadline / tickDuration;
                timeout.remainingRounds = (calculated - tick) / wheel.length;

                final long ticks = Math.max(calculated, tick); // Ensure we don't schedule for past.
                int stopIndex = (int) (ticks & mask);

                HashedWheelBucket bucket = wheel[stopIndex]; //bucket是由HashedWheelTimeout实例组成的一个链表
                bucket.addTimeout(timeout); //单线程(即工作线程)操作，因此不存在并发
            }
        }

        private void processCancelledTasks() {
            for (; ; ) {
                HashedWheelTimeout timeout = cancelledTimeouts.poll();
                if (timeout == null) {
                    // all processed
                    break;
                }
                try {
                    timeout.remove();
                } catch (Throwable t) {
                    if (LOG.isWarnEnabled()) {
                        LOG.warn("An exception was thrown while process a cancellation task", t);
                    }
                }
            }
        }

        /**
         * 根据startTime和当前的tick值，计算目标纳米时间(相对时间)，然后等待该时间点的到来
         * 特别注意该方法的返回值
         * 前面说过，我们用的都是相对时间，因此：
         * 1.第一次进来的时候，工作线程会在t=100ms的时候返回，返回值是100*10^6
         * 2.第二次进来的时候，工作线程会在t=200ms的时候返回，依次类推
         * 另外就是注意极端情况，比如第二次进来的时候，由于前面的任务执行时间超过100ms，为150ms，导致进来的时候就已经是t=250ms
         * 那么，一进入这个方法就要立即返回，返回值是250*10^6，而不是200*10^6
         *
         * @return Long.MIN_VALUE if received a shutdown request,
         * current time otherwise (with Long.MIN_VALUE changed by +1)
         */
        private long waitForNextTick() {
            long deadline = tickDuration * (tick + 1);

            for (; ; ) {
                final long currentTime = System.nanoTime() - startTime;
                //此处+999999作用是向上取整了1ms，不够1ms算1ms
                //假设deadline - currentTime = 1200000，即准确时间是再过1.2ms执行调度，此处选择到2ms，不够的直接进1，即可以延后一点点调度，但不能提前
                long sleepTimeMs = (deadline - currentTime + 999999) / 1000000;

                if (sleepTimeMs <= 0) {
                    if (currentTime == Long.MIN_VALUE) {
                        return -Long.MAX_VALUE;
                    } else {
                        return currentTime;
                    }
                }

                // We decide to remove the original approach (as below) which used in netty for
                // windows platform.
                // See https://github.com/netty/netty/issues/356
                //
                // if (Platform.isWindows()) {
                //     sleepTimeMs = sleepTimeMs / 10 * 10;
                // }
                //
                // The above approach that make sleepTimes to be a multiple of 10ms will
                // lead to severe spin in this loop for several milliseconds, which
                // causes the high CPU usage.
                // See https://github.com/sofastack/sofa-jraft/issues/311
                //
                // According to the regression testing on windows, we haven't reproduced the
                // Thread.sleep() bug referenced in https://www.javamex.com/tutorials/threads/sleep_issues.shtml
                // yet.
                //
                // The regression testing environment:
                // - SOFAJRaft version: 1.2.6
                // - JVM version (e.g. java -version): JDK 1.8.0_191
                // - OS version: Windows 7 ultimate 64 bit
                // - CPU: Intel(R) Core(TM) i7-2670QM CPU @ 2.20GHz (4 cores)

                try {
                    Thread.sleep(sleepTimeMs);
                } catch (InterruptedException ignored) {
                    if (workerStateUpdater.get(HashedWheelTimer.this) == WORKER_STATE_SHUTDOWN) {
                        return Long.MIN_VALUE;
                    }
                }
            }
        }

        public Set<Timeout> unprocessedTimeouts() {
            return Collections.unmodifiableSet(unprocessedTimeouts);
        }
    }

    private static final class HashedWheelTimeout implements Timeout {

        private static final int ST_INIT = 0;
        private static final int ST_CANCELLED = 1;
        private static final int ST_EXPIRED = 2;
        private static final AtomicIntegerFieldUpdater<HashedWheelTimeout> STATE_UPDATER = AtomicIntegerFieldUpdater
                .newUpdater(
                        HashedWheelTimeout.class,
                        "state");

        private final HashedWheelTimer timer;
        private final TimerTask task;
        private final long deadline;

        @SuppressWarnings({"unused", "FieldMayBeFinal", "RedundantFieldInitialization"})
        private volatile int state = ST_INIT;

        // remainingRounds will be calculated and set by Worker.transferTimeoutsToBuckets() before the
        // HashedWheelTimeout will be added to the correct HashedWheelBucket.
        long remainingRounds;

        // This will be used to chain timeouts in HashedWheelTimerBucket via a double-linked-list.
        // As only the workerThread will act on it there is no need for synchronization / volatile.
        HashedWheelTimeout next;
        HashedWheelTimeout prev;

        // The bucket to which the timeout was added
        HashedWheelBucket bucket;

        HashedWheelTimeout(HashedWheelTimer timer, TimerTask task, long deadline) {
            this.timer = timer;
            this.task = task;
            this.deadline = deadline;
        }

        @Override
        public Timer timer() {
            return timer;
        }

        @Override
        public TimerTask task() {
            return task;
        }

        @Override
        public boolean cancel() {
            // only update the state it will be removed from HashedWheelBucket on next tick.
            if (!compareAndSetState(ST_INIT, ST_CANCELLED)) {
                return false;
            }
            // If a task should be canceled we put this to another queue which will be processed on each tick.
            // So this means that we will have a GC latency of max. 1 tick duration which is good enough. This way
            // we can make again use of our MpscLinkedQueue and so minimize the locking / overhead as much as possible.
            timer.cancelledTimeouts.add(this);
            return true;
        }

        void remove() {
            HashedWheelBucket bucket = this.bucket;
            if (bucket != null) {
                bucket.remove(this);
            } else {
                timer.pendingTimeouts.decrementAndGet();
            }
        }

        public boolean compareAndSetState(int expected, int state) {
            return STATE_UPDATER.compareAndSet(this, expected, state);
        }

        public int state() {
            return state;
        }

        @Override
        public boolean isCancelled() {
            return state() == ST_CANCELLED;
        }

        @Override
        public boolean isExpired() {
            return state() == ST_EXPIRED;
        }

        public void expire() {
            if (!compareAndSetState(ST_INIT, ST_EXPIRED)) {
                return;
            }

            try {
                task.run(this);
            } catch (Throwable t) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("An exception was thrown by " + TimerTask.class.getSimpleName() + '.', t);
                }
            }
        }

        @Override
        public String toString() {
            final long currentTime = System.nanoTime();
            long remaining = deadline - currentTime + timer.startTime;

            StringBuilder buf = new StringBuilder(192).append(getClass().getSimpleName()).append('(')
                    .append("deadline: ");
            if (remaining > 0) {
                buf.append(remaining).append(" ns later");
            } else if (remaining < 0) {
                buf.append(-remaining).append(" ns ago");
            } else {
                buf.append("now");
            }

            if (isCancelled()) {
                buf.append(", cancelled");
            }

            return buf.append(", task: ").append(task()).append(')').toString();
        }
    }

    /**
     * Bucket that stores HashedWheelTimeouts. These are stored in a linked-list like datastructure to allow easy
     * removal of HashedWheelTimeouts in the middle. Also the HashedWheelTimeout act as nodes themself and so no
     * extra object creation is needed.
     */
    private static final class HashedWheelBucket {
        // Used for the linked-list datastructure
        private HashedWheelTimeout head;
        private HashedWheelTimeout tail;

        /**
         * Add {@link HashedWheelTimeout} to this bucket.
         */
        public void addTimeout(HashedWheelTimeout timeout) {
            assert timeout.bucket == null;
            timeout.bucket = this;
            if (head == null) {
                head = tail = timeout;
            } else {
                tail.next = timeout;
                timeout.prev = tail;
                tail = timeout;
            }
        }

        /**
         * Expire all {@link HashedWheelTimeout}s for the given {@code deadline}.
         */
        public void expireTimeouts(long deadline) {
            HashedWheelTimeout timeout = head;

            // process all timeouts
            while (timeout != null) {
                HashedWheelTimeout next = timeout.next;
                if (timeout.remainingRounds <= 0) {
                    next = remove(timeout);
                    if (timeout.deadline <= deadline) {
                        timeout.expire();
                    } else {
                        // The timeout was placed into a wrong slot. This should never happen.
                        throw new IllegalStateException(String.format("timeout.deadline (%d) > deadline (%d)",
                                timeout.deadline, deadline));
                    }
                } else if (timeout.isCancelled()) {
                    next = remove(timeout);
                } else {
                    timeout.remainingRounds--;
                }
                timeout = next;
            }
        }

        public HashedWheelTimeout remove(HashedWheelTimeout timeout) {
            HashedWheelTimeout next = timeout.next;
            // remove timeout that was either processed or cancelled by updating the linked-list
            if (timeout.prev != null) {
                timeout.prev.next = next;
            }
            if (timeout.next != null) {
                timeout.next.prev = timeout.prev;
            }

            if (timeout == head) {
                // if timeout is also the tail we need to adjust the entry too
                if (timeout == tail) {
                    tail = null;
                    head = null;
                } else {
                    head = next;
                }
            } else if (timeout == tail) {
                // if the timeout is the tail modify the tail to be the prev node.
                tail = timeout.prev;
            }
            // null out prev, next and bucket to allow for GC.
            timeout.prev = null;
            timeout.next = null;
            timeout.bucket = null;
            timeout.timer.pendingTimeouts.decrementAndGet();
            return next;
        }

        /**
         * Clear this bucket and return all not expired / cancelled {@link Timeout}s.
         */
        public void clearTimeouts(Set<Timeout> set) {
            for (; ; ) {
                HashedWheelTimeout timeout = pollTimeout();
                if (timeout == null) {
                    return;
                }
                if (timeout.isExpired() || timeout.isCancelled()) {
                    continue;
                }
                set.add(timeout);
            }
        }

        private HashedWheelTimeout pollTimeout() {
            HashedWheelTimeout head = this.head;
            if (head == null) {
                return null;
            }
            HashedWheelTimeout next = head.next;
            if (next == null) {
                tail = this.head = null;
            } else {
                this.head = next;
                next.prev = null;
            }

            // null out prev and next to allow for GC.
            head.next = null;
            head.prev = null;
            head.bucket = null;
            return head;
        }
    }
}
