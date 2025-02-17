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
package com.alipay.sofa.jraft.rhea.cmd.store;

import java.util.List;

import com.alipay.sofa.jraft.rhea.storage.KVEntry;

/**
 * @author jiachun.fjc
 */
public class BatchPutRequest extends BaseRequest {

    private static final long serialVersionUID = -980036845124180958L;

    private List<KVEntry> kvEntries;

    public List<KVEntry> getKvEntries() {
        return kvEntries;
    }

    public void setKvEntries(List<KVEntry> kvEntries) {
        this.kvEntries = kvEntries;
    }

    @Override
    public byte magic() {
        return BATCH_PUT;
    }

    @Override
    public String toString() {
        return "BatchPutRequest{" + "kvEntries=" + kvEntries + "} " + super.toString();
    }
}
