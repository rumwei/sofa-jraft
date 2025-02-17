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

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import com.alipay.sofa.jraft.NodeManager;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.rpc.Connection;
import com.alipay.sofa.jraft.rpc.RaftServerService;
import com.alipay.sofa.jraft.rpc.RpcContext;
import com.alipay.sofa.jraft.rpc.RpcRequests.AppendEntriesRequest;
import com.alipay.sofa.jraft.rpc.RpcRequests.PingRequest;
import com.alipay.sofa.jraft.rpc.impl.core.AppendEntriesRequestProcessor.PeerPair;
import com.alipay.sofa.jraft.rpc.impl.core.AppendEntriesRequestProcessor.PeerRequestContext;
import com.alipay.sofa.jraft.test.MockAsyncContext;
import com.alipay.sofa.jraft.test.TestUtils;
import com.alipay.sofa.jraft.util.concurrent.ConcurrentHashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Matchers.eq;

public class AppendEntriesRequestProcessorTest extends BaseNodeRequestProcessorTest<AppendEntriesRequest> {

    private AppendEntriesRequest request;

    private final String serverId = "localhost:8082";

    @Override
    public AppendEntriesRequest createRequest(final String groupId, final PeerId peerId) {
        this.request = AppendEntriesRequest.newBuilder().setCommittedIndex(0). //
                setGroupId(groupId). //
                setPeerId(peerId.toString()).//
                setServerId(this.serverId). //
                setPrevLogIndex(0). //
                setTerm(0). //
                setPrevLogTerm(0).build();
        return this.request;
    }

    @Mock
    private Connection conn;

    @Override
    public void setup() {
        super.setup();
        this.asyncContext = new MockAsyncContext() {
            @Override
            public Connection getConnection() {
                return AppendEntriesRequestProcessorTest.this.conn;
            }
        };
        Set<PeerPair> pairs = new ConcurrentHashSet<>();
        pairs.add(new PeerPair(this.peerIdStr, this.serverId));
        Mockito.when(this.conn.getAttribute(AppendEntriesRequestProcessor.PAIR_ATTR)).thenReturn(pairs);
    }

    private ExecutorService executor;

    @Override
    public NodeRequestProcessor<AppendEntriesRequest> newProcessor() {
        this.executor = Executors.newSingleThreadExecutor();
        return new AppendEntriesRequestProcessor(this.executor);
    }

    @Override
    public void teardown() {
        super.teardown();
        if (this.executor != null) {
            this.executor.shutdownNow();
        }
    }

    @Test
    public void testPairOf() {
        final AppendEntriesRequestProcessor processor = (AppendEntriesRequestProcessor) newProcessor();

        PeerPair pair = processor.pairOf(this.peerIdStr, this.serverId);
        assertEquals(pair.remote, this.serverId);
        assertEquals(pair.local, this.peerIdStr);

        // test constant pool
        assertSame(pair, processor.pairOf(this.peerIdStr, this.serverId));
        assertSame(pair, processor.pairOf(this.peerIdStr, this.serverId));
    }

    @Test
    public void testOnClosed() {
        mockNode();
        final AppendEntriesRequestProcessor processor = (AppendEntriesRequestProcessor) newProcessor();

        PeerPair pair = processor.pairOf(this.peerIdStr, this.serverId);
        final PeerRequestContext ctx = processor.getOrCreatePeerRequestContext(this.groupId, pair, this.conn);
        assertNotNull(ctx);
        assertSame(ctx, processor.getPeerRequestContext(this.groupId, pair));
        assertSame(ctx, processor.getOrCreatePeerRequestContext(this.groupId, pair, this.conn));

        processor.onClosed(null, this.conn);
        assertNull(processor.getPeerRequestContext(this.groupId, pair));
        assertNotSame(ctx, processor.getOrCreatePeerRequestContext(this.groupId, pair, this.conn));
    }

    @Override
    public void verify(final String interest, final RaftServerService service,
                       final NodeRequestProcessor<AppendEntriesRequest> processor) {
        assertEquals(interest, AppendEntriesRequest.class.getName());
        Mockito.verify(service).handleAppendEntriesRequest(eq(this.request), Mockito.any());
        final PeerPair pair = ((AppendEntriesRequestProcessor) processor).pairOf(this.peerIdStr, this.serverId);
        final PeerRequestContext ctx = ((AppendEntriesRequestProcessor) processor).getOrCreatePeerRequestContext(
                this.groupId, pair, this.conn);
        assertNotNull(ctx);
    }

    @Test
    public void testGetPeerRequestContextRemovePeerRequestContext() {
        mockNode();

        final AppendEntriesRequestProcessor processor = (AppendEntriesRequestProcessor) newProcessor();
        final PeerPair pair = processor.pairOf(this.peerIdStr, this.serverId);
        final PeerRequestContext ctx = processor.getOrCreatePeerRequestContext(this.groupId, pair, this.conn);
        assertNotNull(ctx);
        assertSame(ctx, processor.getOrCreatePeerRequestContext(this.groupId, pair, this.conn));
        assertEquals(0, ctx.getNextRequiredSequence());
        assertEquals(0, ctx.getAndIncrementSequence());
        assertEquals(1, ctx.getAndIncrementSequence());
        assertEquals(0, ctx.getAndIncrementNextRequiredSequence());
        assertEquals(1, ctx.getAndIncrementNextRequiredSequence());
        assertFalse(ctx.hasTooManyPendingResponses());

        processor.removePeerRequestContext(this.groupId, pair);
        final PeerRequestContext newCtx = processor.getOrCreatePeerRequestContext(this.groupId, pair, this.conn);
        assertNotNull(newCtx);
        assertNotSame(ctx, newCtx);

        assertEquals(0, newCtx.getNextRequiredSequence());
        assertEquals(0, newCtx.getAndIncrementSequence());
        assertEquals(1, newCtx.getAndIncrementSequence());
        assertEquals(0, newCtx.getAndIncrementNextRequiredSequence());
        assertEquals(1, newCtx.getAndIncrementNextRequiredSequence());
        assertFalse(newCtx.hasTooManyPendingResponses());
    }

    @Test
    public void testSendSequenceResponse() {
        mockNode();
        final AppendEntriesRequestProcessor processor = (AppendEntriesRequestProcessor) newProcessor();
        final PeerPair pair = processor.pairOf(this.peerIdStr, this.serverId);
        processor.getOrCreatePeerRequestContext(this.groupId, pair, this.conn);
        final PingRequest msg = TestUtils.createPingRequest();
        final RpcContext asyncContext = Mockito.mock(RpcContext.class);
        processor.sendSequenceResponse(this.groupId, pair, 1, asyncContext, msg);
        Mockito.verify(asyncContext, Mockito.never()).sendResponse(msg);

        processor.sendSequenceResponse(this.groupId, pair, 0, asyncContext, msg);
        Mockito.verify(asyncContext, Mockito.times(2)).sendResponse(msg);
    }

    @Test
    public void testTooManyPendingResponses() {
        final PeerId peer = mockNode();
        NodeManager.getInstance().get(this.groupId, peer).getRaftOptions().setMaxReplicatorInflightMsgs(2);

        final RpcContext asyncContext = Mockito.mock(RpcContext.class);
        final AppendEntriesRequestProcessor processor = (AppendEntriesRequestProcessor) newProcessor();
        final PeerPair pair = processor.pairOf(this.peerIdStr, this.serverId);
        final PingRequest msg = TestUtils.createPingRequest();
        final Connection conn = Mockito.mock(Connection.class);
        Mockito.when(asyncContext.getConnection()).thenReturn(conn);
        final PeerRequestContext ctx = processor.getOrCreatePeerRequestContext(this.groupId, pair, conn);
        assertNotNull(ctx);
        processor.sendSequenceResponse(this.groupId, pair, 1, asyncContext, msg);
        processor.sendSequenceResponse(this.groupId, pair, 2, asyncContext, msg);
        processor.sendSequenceResponse(this.groupId, pair, 3, asyncContext, msg);
        Mockito.verify(asyncContext, Mockito.never()).sendResponse(msg);
        Mockito.verify(conn).close();

        final PeerRequestContext newCtx = processor.getOrCreatePeerRequestContext(this.groupId, pair, conn);
        assertNotNull(newCtx);
        assertNotSame(ctx, newCtx);
    }

}
