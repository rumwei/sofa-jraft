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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import com.alipay.sofa.jraft.JRaftUtils;
import com.alipay.sofa.jraft.Node;
import com.alipay.sofa.jraft.NodeManager;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.entity.NodeId;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.error.RaftError;
import com.alipay.sofa.jraft.option.NodeOptions;
import com.alipay.sofa.jraft.rpc.RpcRequestClosure;
import com.alipay.sofa.jraft.rpc.RpcRequests.ErrorResponse;
import com.alipay.sofa.jraft.rpc.RpcRequests.PingRequest;
import com.alipay.sofa.jraft.test.MockAsyncContext;
import com.alipay.sofa.jraft.test.TestUtils;
import com.alipay.sofa.jraft.util.Endpoint;
import com.alipay.sofa.jraft.util.RpcFactoryHelper;
import com.google.protobuf.Message;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

@RunWith(MockitoJUnitRunner.class)
public class BaseCliRequestProcessorTest {
    private static class MockCliRequestProcessor extends BaseCliRequestProcessor<PingRequest> {

        private String peerId;
        private String groupId;
        private RpcRequestClosure done;
        private CliRequestContext ctx;

        public MockCliRequestProcessor(String peerId, String groupId) {
            super(null, null);
            this.peerId = peerId;
            this.groupId = groupId;
        }

        @Override
        protected String getPeerId(PingRequest request) {
            return this.peerId;
        }

        @Override
        protected String getGroupId(PingRequest request) {
            return this.groupId;
        }

        @Override
        protected Message processRequest0(CliRequestContext ctx, PingRequest request, RpcRequestClosure done) {
            this.ctx = ctx;
            this.done = done;
            return RpcFactoryHelper.responseFactory().newResponse(null, Status.OK());
        }

        @Override
        public String interest() {
            return PingRequest.class.getName();
        }

    }

    private MockCliRequestProcessor processor;
    private PeerId peer;
    private MockAsyncContext asyncContext;

    @Before
    public void setup() {
        this.asyncContext = new MockAsyncContext();
        this.peer = JRaftUtils.getPeerId("localhost:8081");
        this.processor = new MockCliRequestProcessor(this.peer.toString(), "test");
    }

    @After
    public void teardown() {
        NodeManager.getInstance().clear();
    }

    @Test
    public void testOK() {
        Node node = mockNode(false);

        this.processor.handleRequest(asyncContext, TestUtils.createPingRequest());
        ErrorResponse resp = (ErrorResponse) asyncContext.getResponseObject();
        assertNotNull(this.processor.done);
        assertSame(this.processor.ctx.node, node);
        assertNotNull(resp);
        assertEquals(0, resp.getErrorCode());
    }

    @Test
    public void testDisableCli() {
        mockNode(true);

        this.processor.handleRequest(asyncContext, TestUtils.createPingRequest());
        ErrorResponse resp = (ErrorResponse) asyncContext.getResponseObject();
        assertNotNull(resp);
        assertEquals(RaftError.EACCES.getNumber(), resp.getErrorCode());
        assertEquals("Cli service is not allowed to access node <test/localhost:8081>", resp.getErrorMsg());
    }

    private Node mockNode(boolean disableCli) {
        Node node = Mockito.mock(Node.class);
        Mockito.when(node.getGroupId()).thenReturn("test");
        Mockito.when(node.getNodeId()).thenReturn(new NodeId("test", this.peer.copy()));
        NodeOptions opts = new NodeOptions();
        opts.setDisableCli(disableCli);
        Mockito.when(node.getOptions()).thenReturn(opts);
        NodeManager.getInstance().addAddress(this.peer.getEndpoint());
        NodeManager.getInstance().add(node);
        return node;
    }

    @Test
    public void testInvalidPeerId() {
        this.processor = new MockCliRequestProcessor("localhost", "test");
        this.processor.handleRequest(asyncContext, TestUtils.createPingRequest());
        ErrorResponse resp = (ErrorResponse) asyncContext.getResponseObject();
        assertNotNull(resp);
        assertEquals(RaftError.EINVAL.getNumber(), resp.getErrorCode());
        assertEquals("Fail to parse peer: localhost", resp.getErrorMsg());
    }

    @Test
    public void testEmptyNodes() {
        this.processor = new MockCliRequestProcessor(null, "test");
        this.processor.handleRequest(asyncContext, TestUtils.createPingRequest());
        ErrorResponse resp = (ErrorResponse) asyncContext.getResponseObject();
        assertNotNull(resp);
        assertEquals(RaftError.ENOENT.getNumber(), resp.getErrorCode());
        assertEquals("Empty nodes in group test", resp.getErrorMsg());
    }

    @Test
    public void testManyNodes() {
        Node node1 = Mockito.mock(Node.class);
        Mockito.when(node1.getGroupId()).thenReturn("test");
        Mockito.when(node1.getNodeId()).thenReturn(new NodeId("test", new PeerId("localhost", 8081)));
        NodeOptions opts = new NodeOptions();
        Mockito.when(node1.getOptions()).thenReturn(opts);
        NodeManager.getInstance().addAddress(new Endpoint("localhost", 8081));
        NodeManager.getInstance().add(node1);

        Node node2 = Mockito.mock(Node.class);
        Mockito.when(node2.getGroupId()).thenReturn("test");
        Mockito.when(node2.getNodeId()).thenReturn(new NodeId("test", new PeerId("localhost", 8082)));
        Mockito.when(node2.getOptions()).thenReturn(opts);
        NodeManager.getInstance().addAddress(new Endpoint("localhost", 8082));
        NodeManager.getInstance().add(node2);

        this.processor = new MockCliRequestProcessor(null, "test");
        this.processor.handleRequest(asyncContext, TestUtils.createPingRequest());
        ErrorResponse resp = (ErrorResponse) asyncContext.getResponseObject();
        assertNotNull(resp);
        assertEquals(RaftError.EINVAL.getNumber(), resp.getErrorCode());
        assertEquals("Peer must be specified since there're 2 nodes in group test", resp.getErrorMsg());
    }

    @Test
    public void testSingleNode() {
        Node node = this.mockNode(false);
        this.processor = new MockCliRequestProcessor(null, "test");
        this.processor.handleRequest(asyncContext, TestUtils.createPingRequest());
        ErrorResponse resp = (ErrorResponse) asyncContext.getResponseObject();
        assertNotNull(resp);
        assertSame(this.processor.ctx.node, node);
        assertNotNull(resp);
        assertEquals(0, resp.getErrorCode());
    }

    @Test
    public void testPeerIdNotFound() {
        this.processor.handleRequest(asyncContext, TestUtils.createPingRequest());
        ErrorResponse resp = (ErrorResponse) asyncContext.getResponseObject();
        assertNotNull(resp);
        assertEquals(RaftError.ENOENT.getNumber(), resp.getErrorCode());
        assertEquals("Fail to find node localhost:8081 in group test", resp.getErrorMsg());
    }
}
