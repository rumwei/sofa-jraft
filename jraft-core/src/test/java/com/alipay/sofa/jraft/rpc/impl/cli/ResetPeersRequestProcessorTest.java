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
package com.alipay.sofa.jraft.rpc.impl.cli;

import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.JRaftUtils;
import com.alipay.sofa.jraft.Node;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.rpc.CliRequests.ResetPeerRequest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ResetPeersRequestProcessorTest extends AbstractCliRequestProcessorTest<ResetPeerRequest> {

    @Override
    public ResetPeerRequest createRequest(String groupId, PeerId peerId) {
        return ResetPeerRequest.newBuilder(). //
                setGroupId(groupId). //
                setPeerId(peerId.toString()). //
                addNewPeers("localhost:8084").addNewPeers("localhost:8085").build();
    }

    @Override
    public BaseCliRequestProcessor<ResetPeerRequest> newProcessor() {
        return new ResetPeerRequestProcessor(null);
    }

    @Override
    public void verify(String interest, Node node, ArgumentCaptor<Closure> doneArg) {
        assertEquals(interest, ResetPeerRequest.class.getName());
        Mockito.verify(node).resetPeers(JRaftUtils.getConfiguration("localhost:8084,localhost:8085"));
        assertNotNull(asyncContext.getResponseObject());
    }

}
