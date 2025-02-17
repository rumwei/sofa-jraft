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

import com.alipay.sofa.jraft.util.BytesUtil;

/**
 * @author jiachun.fjc
 */
public class GetRequest extends BaseRequest {

    private static final long serialVersionUID = -864939889102077919L;

    private byte[] key;
    private boolean readOnlySafe = true;

    public byte[] getKey() {
        return key;
    }

    public void setKey(byte[] key) {
        this.key = key;
    }

    public boolean isReadOnlySafe() {
        return readOnlySafe;
    }

    public void setReadOnlySafe(boolean readOnlySafe) {
        this.readOnlySafe = readOnlySafe;
    }

    @Override
    public byte magic() {
        return GET;
    }

    @Override
    public String toString() {
        return "GetRequest{" + "key=" + BytesUtil.toHex(key) + ", readOnlySafe=" + readOnlySafe + "} "
                + super.toString();
    }
}
