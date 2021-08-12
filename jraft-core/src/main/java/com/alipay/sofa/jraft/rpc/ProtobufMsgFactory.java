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
package com.alipay.sofa.jraft.rpc;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.alipay.sofa.jraft.util.SlsLogUtil;
import com.aliyun.openservices.log.util.JsonUtils;
import org.apache.commons.lang.SerializationException;

import com.alipay.sofa.jraft.error.MessageClassNotFoundException;
import com.alipay.sofa.jraft.storage.io.ProtoBufFile;
import com.alipay.sofa.jraft.util.RpcFactoryHelper;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Message;

import static java.lang.invoke.MethodType.methodType;

/**
 * Protobuf message factory.
 *
 * jraft的ProtobufMsgFactory类使用protobuf的动态解析机制，来处理消息，所谓动态解析，就是消费者不根据proto文件编译生成的类来反序列化消息，
 * 而是通过proto文件生成的descriptor来构造动态消息类，然后反序列化（解析）消息。ProtoBuf提供了动态解析机制来解决这个问题，它要求提供二进制
 * 内容的基础上，再提供对应类的Descriptor对象，在解析时通过DynamicMessage类的成员方法来获得对象结果
 *
 *
 */
public class ProtobufMsgFactory {

    private static Map<String/* class name in proto file */, MethodHandle> PARSE_METHODS_4PROTO = new HashMap<>();
    private static Map<String/* class name in java file */, MethodHandle> PARSE_METHODS_4J = new HashMap<>();
    private static Map<String/* class name in java file */, MethodHandle> DEFAULT_INSTANCE_METHODS_4J = new HashMap<>();

    static {
        SlsLogUtil.info("RaftRpcServerCreateAndInit", "traceId", "执行ProtobufMsgFactory类静态代码块");
        try {
            //借助googleProtoBuf提供的FileDescriptorSet，结合配置目录resources/raft.desc文件内容
            //加载该resources下的cli.proto、enum.proto、local_file_meta.proto、local_storage.proto、log.proto、raft.proto、rpc.proto这7个文件
            final FileDescriptorSet descriptorSet = FileDescriptorSet.parseFrom(ProtoBufFile.class.getResourceAsStream("/raft.desc"));
            final List<FileDescriptor> resolveFDs = new ArrayList<>();
            final RaftRpcFactory rpcFactory = RpcFactoryHelper.rpcFactory();
            for (final FileDescriptorProto fdp : descriptorSet.getFileList()) {

                final FileDescriptor[] dependencies = new FileDescriptor[resolveFDs.size()];
                resolveFDs.toArray(dependencies);

                final FileDescriptor fd = FileDescriptor.buildFrom(fdp, dependencies);
                resolveFDs.add(fd);
                for (final Descriptor descriptor : fd.getMessageTypes()) {

                    final String className = fdp.getOptions().getJavaPackage() + "."
                            + fdp.getOptions().getJavaOuterClassname() + "$" + descriptor.getName();
                    final Class<?> clazz = Class.forName(className);
                    final MethodHandle parseFromHandler = MethodHandles.lookup().findStatic(clazz, "parseFrom",
                            methodType(clazz, byte[].class));
                    final MethodHandle getInstanceHandler = MethodHandles.lookup().findStatic(clazz,
                            "getDefaultInstance", methodType(clazz));
                    PARSE_METHODS_4PROTO.put(descriptor.getFullName(), parseFromHandler);
                    PARSE_METHODS_4J.put(className, parseFromHandler);
                    DEFAULT_INSTANCE_METHODS_4J.put(className, getInstanceHandler);
                    rpcFactory.registerProtobufSerializer(className, getInstanceHandler.invoke());
                }

            }

            /**
             * JsonUtils.serialize(PARSE_METHODS_4PROTO)结果：
             * {
             *     "jraft.GetFileResponse": {
             *         "varargsCollector": false
             *     },
             *     "jraft.ReadIndexResponse": {
             *         "varargsCollector": false
             *     },
             *     "jraft.ChangePeersRequest": {
             *         "varargsCollector": false
             *     },
             *     "jraft.GetLeaderResponse": {
             *         "varargsCollector": false
             *     },
             *     "jraft.AddLearnersRequest": {
             *         "varargsCollector": false
             *     },
             *     "jraft.EntryMeta": {
             *         "varargsCollector": false
             *     },
             *     "jraft.LogPBMeta": {
             *         "varargsCollector": false
             *     },
             *     "jraft.AddPeerRequest": {
             *         "varargsCollector": false
             *     },
             *     "jraft.RemoveLearnersRequest": {
             *         "varargsCollector": false
             *     },
             *     "jraft.LocalFileMeta": {
             *         "varargsCollector": false
             *     },
             *     "jraft.LearnersOpResponse": {
             *         "varargsCollector": false
             *     },
             *     "jraft.AddPeerResponse": {
             *         "varargsCollector": false
             *     },
             *     "jraft.GetPeersRequest": {
             *         "varargsCollector": false
             *     },
             *     "jraft.ConfigurationPBMeta": {
             *         "varargsCollector": false
             *     },
             *     "jraft.RemovePeerResponse": {
             *         "varargsCollector": false
             *     },
             *     "jraft.TransferLeaderRequest": {
             *         "varargsCollector": false
             *     },
             *     "jraft.GetPeersResponse": {
             *         "varargsCollector": false
             *     },
             *     "jraft.ReadIndexRequest": {
             *         "varargsCollector": false
             *     },
             *     "jraft.PingRequest": {
             *         "varargsCollector": false
             *     },
             *     "jraft.RequestVoteRequest": {
             *         "varargsCollector": false
             *     },
             *     "jraft.InstallSnapshotRequest": {
             *         "varargsCollector": false
             *     },
             *     "jraft.AppendEntriesRequest": {
             *         "varargsCollector": false
             *     },
             *     "jraft.TimeoutNowRequest": {
             *         "varargsCollector": false
             *     },
             *     "jraft.RequestVoteResponse": {
             *         "varargsCollector": false
             *     },
             *     "jraft.InstallSnapshotResponse": {
             *         "varargsCollector": false
             *     },
             *     "jraft.SnapshotRequest": {
             *         "varargsCollector": false
             *     },
             *     "jraft.ChangePeersResponse": {
             *         "varargsCollector": false
             *     },
             *     "jraft.TimeoutNowResponse": {
             *         "varargsCollector": false
             *     },
             *     "jraft.ResetPeerRequest": {
             *         "varargsCollector": false
             *     },
             *     "jraft.AppendEntriesResponse": {
             *         "varargsCollector": false
             *     },
             *     "jraft.ResetLearnersRequest": {
             *         "varargsCollector": false
             *     },
             *     "jraft.ErrorResponse": {
             *         "varargsCollector": false
             *     },
             *     "jraft.GetLeaderRequest": {
             *         "varargsCollector": false
             *     },
             *     "jraft.GetFileRequest": {
             *         "varargsCollector": false
             *     },
             *     "jraft.AppendEntriesRequestHeader": {
             *         "varargsCollector": false
             *     },
             *     "jraft.SnapshotMeta": {
             *         "varargsCollector": false
             *     },
             *     "jraft.LocalSnapshotPbMeta": {
             *         "varargsCollector": false
             *     },
             *     "jraft.RemovePeerRequest": {
             *         "varargsCollector": false
             *     },
             *     "jraft.StablePBMeta": {
             *         "varargsCollector": false
             *     },
             *     "jraft.PBLogEntry": {
             *         "varargsCollector": false
             *     }
             * }
             */

            /**
             * JsonUtils.serialize(PARSE_METHODS_4J)结果
             * {
             *     "com.alipay.sofa.jraft.entity.RaftOutter$EntryMeta": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.RpcRequests$TimeoutNowResponse": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.CliRequests$GetLeaderRequest": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.CliRequests$GetLeaderResponse": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.CliRequests$RemoveLearnersRequest": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.CliRequests$GetPeersResponse": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.CliRequests$RemovePeerRequest": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.RpcRequests$RequestVoteRequest": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.RpcRequests$ReadIndexResponse": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.RpcRequests$ErrorResponse": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.RpcRequests$GetFileResponse": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.RpcRequests$AppendEntriesResponse": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.RpcRequests$AppendEntriesRequest": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.CliRequests$AddPeerResponse": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.CliRequests$GetPeersRequest": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.RpcRequests$AppendEntriesRequestHeader": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.CliRequests$ResetPeerRequest": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.RpcRequests$GetFileRequest": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.CliRequests$ResetLearnersRequest": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.RpcRequests$PingRequest": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.RpcRequests$InstallSnapshotResponse": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.entity.LocalStorageOutter$ConfigurationPBMeta": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.entity.LocalFileMetaOutter$LocalFileMeta": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.RpcRequests$ReadIndexRequest": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.CliRequests$SnapshotRequest": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.RpcRequests$RequestVoteResponse": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.CliRequests$LearnersOpResponse": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.entity.RaftOutter$SnapshotMeta": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.entity.LocalStorageOutter$StablePBMeta": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.entity.codec.v2.LogOutter$PBLogEntry": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.CliRequests$AddPeerRequest": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.entity.LocalStorageOutter$LogPBMeta": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.CliRequests$ChangePeersResponse": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.entity.LocalStorageOutter$LocalSnapshotPbMeta": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.CliRequests$AddLearnersRequest": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.CliRequests$ChangePeersRequest": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.CliRequests$TransferLeaderRequest": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.RpcRequests$InstallSnapshotRequest": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.RpcRequests$TimeoutNowRequest": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.CliRequests$RemovePeerResponse": {
             *         "varargsCollector": false
             *     }
             * }
             */

            /**
             * JsonUtils.serialize(DEFAULT_INSTANCE_METHODS_4J)结果：
             * {
             *     "com.alipay.sofa.jraft.entity.RaftOutter$EntryMeta": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.RpcRequests$TimeoutNowResponse": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.CliRequests$GetLeaderRequest": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.CliRequests$GetLeaderResponse": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.CliRequests$RemoveLearnersRequest": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.CliRequests$GetPeersResponse": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.CliRequests$RemovePeerRequest": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.RpcRequests$RequestVoteRequest": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.RpcRequests$ReadIndexResponse": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.RpcRequests$ErrorResponse": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.RpcRequests$GetFileResponse": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.RpcRequests$AppendEntriesResponse": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.RpcRequests$AppendEntriesRequest": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.CliRequests$AddPeerResponse": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.CliRequests$GetPeersRequest": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.RpcRequests$AppendEntriesRequestHeader": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.CliRequests$ResetPeerRequest": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.RpcRequests$GetFileRequest": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.CliRequests$ResetLearnersRequest": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.RpcRequests$PingRequest": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.RpcRequests$InstallSnapshotResponse": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.entity.LocalStorageOutter$ConfigurationPBMeta": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.entity.LocalFileMetaOutter$LocalFileMeta": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.RpcRequests$ReadIndexRequest": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.CliRequests$SnapshotRequest": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.RpcRequests$RequestVoteResponse": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.CliRequests$LearnersOpResponse": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.entity.RaftOutter$SnapshotMeta": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.entity.LocalStorageOutter$StablePBMeta": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.entity.codec.v2.LogOutter$PBLogEntry": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.CliRequests$AddPeerRequest": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.entity.LocalStorageOutter$LogPBMeta": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.CliRequests$ChangePeersResponse": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.entity.LocalStorageOutter$LocalSnapshotPbMeta": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.CliRequests$AddLearnersRequest": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.CliRequests$ChangePeersRequest": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.CliRequests$TransferLeaderRequest": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.RpcRequests$InstallSnapshotRequest": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.RpcRequests$TimeoutNowRequest": {
             *         "varargsCollector": false
             *     },
             *     "com.alipay.sofa.jraft.rpc.CliRequests$RemovePeerResponse": {
             *         "varargsCollector": false
             *     }
             * }
             */

        } catch (final Throwable t) {
            t.printStackTrace(); // NOPMD
        }
    }

    public static void load() {
        if (PARSE_METHODS_4J.isEmpty() || PARSE_METHODS_4PROTO.isEmpty() || DEFAULT_INSTANCE_METHODS_4J.isEmpty()) {
            throw new IllegalStateException("Parse protocol file failed.");
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends Message> T getDefaultInstance(final String className) {
        final MethodHandle handle = DEFAULT_INSTANCE_METHODS_4J.get(className);
        if (handle == null) {
            throw new MessageClassNotFoundException(className + " not found");
        }
        try {
            return (T) handle.invoke();
        } catch (Throwable t) {
            throw new SerializationException(t);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends Message> T newMessageByJavaClassName(final String className, final byte[] bs) {
        final MethodHandle handle = PARSE_METHODS_4J.get(className);
        if (handle == null) {
            throw new MessageClassNotFoundException(className + " not found");
        }
        try {
            return (T) handle.invoke(bs);
        } catch (Throwable t) {
            throw new SerializationException(t);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends Message> T newMessageByProtoClassName(final String className, final byte[] bs) {
        final MethodHandle handle = PARSE_METHODS_4PROTO.get(className);
        if (handle == null) {
            throw new MessageClassNotFoundException(className + " not found");
        }
        try {
            return (T) handle.invoke(bs);
        } catch (Throwable t) {
            throw new SerializationException(t);
        }
    }
}
