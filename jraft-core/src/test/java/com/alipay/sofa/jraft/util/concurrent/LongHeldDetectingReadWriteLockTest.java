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
package com.alipay.sofa.jraft.util.concurrent;

import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author jiachun.fjc
 */
public class LongHeldDetectingReadWriteLockTest {

    @Test
    public void testLongHeldWriteLock() throws InterruptedException {
        final ReadWriteLock readWriteLock = new LongHeldDetectingReadWriteLock(100, TimeUnit.MILLISECONDS) {

            @Override
            public void report(AcquireMode acquireMode, Thread owner, Collection<Thread> queuedThreads, long blockedNanos) {
                System.out.println("currentThread=" + Thread.currentThread() +
                        " acquireMode=" + acquireMode +
                        " lockOwner=" + owner +
                        " queuedThreads= " + queuedThreads +
                        " blockedMs=" + TimeUnit.NANOSECONDS.toMillis(blockedNanos));

                Assert.assertTrue(Thread.currentThread().getName().contains("read-lock-thread"));
                Assert.assertSame(AcquireMode.Read, acquireMode);
                Assert.assertEquals("write-lock-thread", owner.getName());
                Assert.assertEquals(2000, TimeUnit.NANOSECONDS.toMillis(blockedNanos), 100);
            }
        };

        final CountDownLatch latch = new CountDownLatch(1);
        new Thread(() -> {
            readWriteLock.writeLock().lock();
            latch.countDown();
            try {
                Thread.sleep(2000);
            } catch (final InterruptedException e) {
                e.printStackTrace();
            } finally {
                readWriteLock.writeLock().unlock();
            }
        }, "write-lock-thread") //
                .start();

        latch.await();

        final CountDownLatch latch1 = new CountDownLatch(2);
        new Thread(() -> {
            readWriteLock.readLock().lock();
            readWriteLock.readLock().unlock();
            latch1.countDown();
        }, "read-lock-thread-1").start();

        new Thread(() -> {
            readWriteLock.readLock().lock();
            readWriteLock.readLock().unlock();
            latch1.countDown();
        }, "read-lock-thread-2").start();

        latch1.await();
    }

    @Test
    public void testLongHeldReadLock() throws InterruptedException {
        final ReadWriteLock readWriteLock = new LongHeldDetectingReadWriteLock(100, TimeUnit.MILLISECONDS) {

            @Override
            public void report(AcquireMode acquireMode, Thread owner, Collection<Thread> queuedThreads, long blockedNanos) {
                System.out.println("currentThread=" + Thread.currentThread() +
                        " acquireMode=" + acquireMode +
                        " lockOwner=" + owner +
                        " queuedThreads= " + queuedThreads +
                        " blockedMs=" + TimeUnit.NANOSECONDS.toMillis(blockedNanos));

                Assert.assertTrue(Thread.currentThread().getName().contains("write-lock-thread"));
                Assert.assertSame(AcquireMode.Write, acquireMode);
                Assert.assertNull(owner);
                Assert.assertEquals(2000, TimeUnit.NANOSECONDS.toMillis(blockedNanos), 100);
            }
        };

        final CountDownLatch latch = new CountDownLatch(1);
        new Thread(() -> {
            readWriteLock.readLock().lock();
            latch.countDown();
            try {
                Thread.sleep(2000);
            } catch (final InterruptedException e) {
                e.printStackTrace();
            } finally {
                readWriteLock.readLock().unlock();
            }
        }, "read-lock-thread") //
                .start();

        latch.await();

        final CountDownLatch latch1 = new CountDownLatch(2);
        new Thread(() -> {
            readWriteLock.writeLock().lock();
            readWriteLock.writeLock().unlock();
            latch1.countDown();
        }, "write-lock-thread-1").start();

        new Thread(() -> {
            readWriteLock.writeLock().lock();
            readWriteLock.writeLock().unlock();
            latch1.countDown();
        }, "write-lock-thread-2").start();

        latch1.await();
    }
}
