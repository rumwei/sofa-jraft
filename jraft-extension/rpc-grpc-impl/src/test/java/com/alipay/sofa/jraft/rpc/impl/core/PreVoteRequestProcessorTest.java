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
package com.alipay.sofa.jraft.rpc.impl.core;

import org.mockito.Mockito;

import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.rpc.RaftServerService;
import com.alipay.sofa.jraft.rpc.RpcRequests.RequestVoteRequest;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.eq;

public class PreVoteRequestProcessorTest extends BaseNodeRequestProcessorTest<RequestVoteRequest> {
    private RequestVoteRequest request;

    @Override
    public RequestVoteRequest createRequest(String groupId, PeerId peerId) {
        request = RequestVoteRequest.newBuilder().setGroupId(groupId). //
                setServerId("localhostL8082"). //
                setPeerId(peerId.toString()). //
                setTerm(0). //
                setLastLogIndex(0). //
                setLastLogTerm(0). //
                setPreVote(false).build();
        return request;
    }

    @Override
    public NodeRequestProcessor<RequestVoteRequest> newProcessor() {
        return new RequestVoteRequestProcessor(null);
    }

    @Override
    public void verify(String interest, RaftServerService service, NodeRequestProcessor<RequestVoteRequest> processor) {
        assertEquals(interest, RequestVoteRequest.class.getName());
        Mockito.verify(service).handleRequestVoteRequest(eq(request));
    }

}
