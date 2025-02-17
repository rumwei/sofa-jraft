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
package com.alipay.sofa.jraft.rhea.benchmark.raw;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import com.alipay.sofa.jraft.util.BytesUtil;

import static com.alipay.sofa.jraft.rhea.benchmark.BenchmarkUtil.CONCURRENCY;
import static com.alipay.sofa.jraft.rhea.benchmark.BenchmarkUtil.VALUE_BYTES;

/**
 * @author jiachun.fjc
 */
@State(Scope.Benchmark)
public class RawKVApproximateBenchmark extends BaseRawStoreBenchmark {

    /**
     * //
     * // 100w keys, each value is 100 bytes.
     * //
     * // tps = 0.025 * 1000 = 25 ops/second
     * //
     * Benchmark                                                                                Mode  Cnt     Score     Error   Units
     * RawKVApproximateBenchmark.getApproximateKeysInRange                                     thrpt    3     0.025 ±   0.001  ops/ms
     * RawKVApproximateBenchmark.getApproximateKeysInRange                                      avgt    3  1304.013 ± 115.808   ms/op
     * RawKVApproximateBenchmark.getApproximateKeysInRange                                    sample  693  1308.347 ±   5.718   ms/op
     * RawKVApproximateBenchmark.getApproximateKeysInRange:getApproximateKeysInRange·p0.00    sample       1153.434             ms/op
     * RawKVApproximateBenchmark.getApproximateKeysInRange:getApproximateKeysInRange·p0.50    sample       1308.623             ms/op
     * RawKVApproximateBenchmark.getApproximateKeysInRange:getApproximateKeysInRange·p0.90    sample       1366.504             ms/op
     * RawKVApproximateBenchmark.getApproximateKeysInRange:getApproximateKeysInRange·p0.95    sample       1384.120             ms/op
     * RawKVApproximateBenchmark.getApproximateKeysInRange:getApproximateKeysInRange·p0.99    sample       1415.703             ms/op
     * RawKVApproximateBenchmark.getApproximateKeysInRange:getApproximateKeysInRange·p0.999   sample       1457.521             ms/op
     * RawKVApproximateBenchmark.getApproximateKeysInRange:getApproximateKeysInRange·p0.9999  sample       1457.521             ms/op
     * RawKVApproximateBenchmark.getApproximateKeysInRange:getApproximateKeysInRange·p1.00    sample       1457.521             ms/op
     * RawKVApproximateBenchmark.getApproximateKeysInRange                                        ss    3  1158.760 ± 894.444   ms/op
     */

    private static final int KEY_COUNT = 1000000;

    @Setup
    public void setup() {
        try {
            super.setup();
        } catch (Exception e) {
            e.printStackTrace();
        }

        for (int i = 0; i < KEY_COUNT; i++) {
            byte[] key = BytesUtil.writeUtf8("approximate_" + i);
            super.kvStore.put(key, VALUE_BYTES, null);
        }
    }

    @TearDown
    public void tearDown() {
        try {
            super.tearDown();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.All)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void getApproximateKeysInRange() {
        super.kvStore.getApproximateKeysInRange(null, BytesUtil.writeUtf8("approximate_" + (KEY_COUNT - 1)));
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder() //
                .include(RawKVApproximateBenchmark.class.getSimpleName()) //
                .warmupIterations(3) //
                .warmupTime(TimeValue.seconds(10)) //
                .measurementIterations(3) //
                .measurementTime(TimeValue.seconds(10)) //
                .threads(CONCURRENCY) //
                .forks(1) //
                .build();

        new Runner(opt).run();
    }
}
