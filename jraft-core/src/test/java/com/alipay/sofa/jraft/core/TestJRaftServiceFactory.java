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
package com.alipay.sofa.jraft.core;

import com.alipay.sofa.jraft.option.RaftOptions;
import com.alipay.sofa.jraft.storage.LogStorage;
import com.alipay.sofa.jraft.storage.log.RocksDBSegmentLogStorage;

public class TestJRaftServiceFactory extends DefaultJRaftServiceFactory {

    @Override
    public LogStorage createLogStorage(final String uri, final RaftOptions raftOptions) {
        return RocksDBSegmentLogStorage.builder(uri, raftOptions) //
                .setPreAllocateSegmentCount(1) //
                .setKeepInMemorySegmentCount(2) //
                .setMaxSegmentFileSize(512 * 1024) //
                .setValueSizeThreshold(0) //
                .build();
    }

}
