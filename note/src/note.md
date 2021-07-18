# Jraft提供的线性一致读
* 线性一致读 

一个简单的例子就是在 t1 的时间我们写入了一个值，那么在 t1 之后，我们的读一定能读到这个值，不可能读到 t1 之前的值。

因为Raft本来就是一个为了实现分布式环境下面线性一致性的算法，所以我们可以通过Raft非常方便的实现线性一致性读，也就是将任何的读请求走一次
Raft log，等这个log被commit之后，在apply的时候从状态机里面读取值，我们就一定能够保证这个读取到的值是满足线性要求的。

当然，大家知道，因为每次read都需要走Raft流程，所以性能是非常的低效的，所以大家通常都不会使用。

优化：
Raft里面，节点有三个状态，leader，candidate和follower，任何Raft的写入操作都必须经过leader，只有leader将对应的raft log 复制到
majority的节点上面，我们才会认为这一次写入是成功的。所以我们可以认为，如果当前leader能确定一定是leader，那么我们就可以直接在这个
leader上面读取数据，因为对于leader来说，如果确认一个log已经提交到了大多数节点，在t1的时候apply写入到状态机，那么在t1之后后面的read
就一定能读取到这个新写入的数据。

那么如何确认leader在处理这次read的时候一定是leader呢？在 Raft 论文里面，提到了两种方法。

* ReadIndex Read

第一种就是 ReadIndex，当leader要处理一个读请求的时候：

将当前自己的commit index记录到一个local变量ReadIndex里面。
向其他节点发起一次heartbeat，如果大多数节点返回了对应的heartbeat response，那么leader就能够确定现在自己仍然是leader。
Leader等待自己的状态机执行，直到apply index超过了ReadIndex，这样就能够安全的提供linearized read 了。
Leader执行read请求，将结果返回给 client。
可以看到，不同于最开始的通过Raft log的read，ReadIndex read使用了heartbeat的方式来让leader确认自己是leader，省去了Raft log
那一套流程。虽然仍然会有网络开销，但heartbeat本来就很小，所以性能还是非常好的。

但这里，需要注意，实现ReadIndex的时候有一个corner case，即leader刚通过选举成为leader的时候，这时候的commit index并不能够保证是当前
整个系统最新的commit index，所以Raft要求当leader选举成功之后，首先提交一个no-op的 entry，保证leader的commit index 成为最新的。

所以，如果在no-op的entry还没提交成功之前，leader是不能够处理ReadIndex的。解决的方法也很简单，因为leader在选举成功之后，term一定会增加，在
处理ReadIndex的时候，如果当前最新的commit log的term还没到新的term，就会一直等待直到跟新的term一致，也就是no-op entry提交之后，才可以对外
处理ReadIndex。

使用ReadIndex，我们也可以非常方便的提供follower read的功能，follower收到read请求之后，直接给leader发送一个获取ReadIndex的命令，leader
仍然走一遍之前的流程，然后将ReadIndex返回给follower，follower等到当前的状态机的apply index超过ReadIndex之后，就可以read
然后将结果返回给client了。

* Lease Read
虽然ReadIndex比原来的Raft log read快了很多，但毕竟还是有Heartbeat的开销，所以我们可以考虑做更进一步的优化。

在Raft论文里面，提到了一种通过clock + heartbeat的lease read优化方法。也就是leader发送heartbeat的时候，会首先记录一个时间点start，当系统大
部分节点都回复了heartbeat response，那么我们就可以认为leader的lease有效期可以到start + election timeout / clock drift bound这个时间点。

为什么能够这么认为呢？主要是在于Raft的选举机制，因为follower会在至少election timeout的时间之后，才会重新发生选举，所以下一个leader选出来的时
间一定可以保证大于start + election timeout / clock drift bound。

虽然采用lease的做法很高效，但仍然会面临风险问题，也就是我们有了一个预设的前提，各个服务器的CPU clock的时间是准的，即使有误差，也会在一个非常小的
bound范围里面，如果各个服务器之间clock走的频率不一样，有些太快，有些太慢，这套lease机制就可能出问题。

Jraft对Read Index Read和Lease Read都做了更高效率的实现。如{@link AtomicRangeGroup#readFromQuorum}就使用到了一致性读

Jraft默认提供的线性一致性读是Read Index实现的，如果要使用基于Lease Read，可以通过服务端设置RaftOptions.ReadOnlyOption为ReadOnlyLeaseBased
来切换。

# Jraft的故障与保证
以下说明以下Raft Group可能遇到的故障，以及在各种故障情况下的一致性和可用性保障，故障包括
* 机器断电
* 强杀应用
* 节点运行缓慢，比如OOM，无法正常提供服务
* 网络故障，比如网络传输缓慢或者产生分区
* 其他可能导致Raft节点无法工作的问题

a.单个节点故障或者不大于半数的少数节点故障
该种情况对于整个Raft Group来说，可以继续提供读服务，短暂无法提供写服务，数据一致性没有影响。
1.如果故障节点中包含leader节点，则Raft Group在最多election timeout之后会进行重新选举，选出新的leader，在新leader产生之前，写入服务终止，
可以提供读服务，但可能会频繁遇到脏读，线性一致性也将无法工作。
2.如果故障节点中不包含leader节点，读和写都没有影响，仅仅是发往这些故障follower节点的请求会失败，应用应当重试这些请求到正常节点中。

b.大于半数的多数节点故障
该种情况，无论是否包含leader节点，整个Raft Group都将不具有可用性，未故障的少数节点仍然能提供只读服务，但写服务无法恢复，应尽快恢复故障节点，达到过半数。
在故障节点无法快速恢复的情况下，可以通过{@link CliService#resetPeers(Configuration)}方法强制设置剩余存活节点的配置，丢弃故障节点，以便让
剩余节点尽快选出新的leader，代价是可能丢失数据，失去一致性承诺，只有在非常紧急并且可用性更为重要的情况下才推荐使用。

# Jraft使用
## RPC建议
* 建议开启CliService服务，方便查询和管理Raft集群
* 是否复用RPC Server取决于应用，如果都使用bolt RPC，建议复用
* Task的data序列化采用注入protobuf等性能和空间相对均衡的方案
* 业务RPC processor不要与JRaft RPC processor共用线程池，避免影响RAFT内部协议交互

## 客户端建议
* 使用RoutTable管理集群信息，定期refreshLeader和refreshConfiguration获取集群最新状态
* 业务协议应当内置Redirect重定向请求协议，当写入到非leader节点，返回最新leader信息到客户端，客户端再做重试
* 建议使用线性一致读，将请求散列到集群内的所有节点上，降低leader的负荷压力

## 磁盘建议
JRaft一般对磁盘的延时较为敏感，因此可以使用linux ionice命令来提高jraft进程(如下pid对应进程)的磁盘优先级
sudo ionice -c2 -n0 -p pid

## 网络
当JRaft leader并发处理的请求较多时，可能会造成与follower的通信延时，但实际上在JRaft中，leader与follower的通信优先级是高于leader与客户端
的通信的，因此可以通过linux tc命令来设置不同流量的优先级。

## 基于SPI扩展
如果需要基于SPI扩展适配新LogEntry编/解码器，可以遵循如下步骤
* 实现com.alipay.sofa.jraft.JRaftServiceFactory创建服务工厂接口
* 添加注解@SPI到LogEntryCodecFactory实现类，设置优先级priorty注解属性
* 在自己工程目录(META-INF.service)添加com.alipay.sofa.jraft.JRaftServiceFactory文本
* 实现com.alipay.sofa.jraft.entity.codec.LogEntryCodecFactory，即LogEntry编/解码工厂接口
* JRaftServiceFactory自定义实现指定新的LogEntryCodecFactory

# Learner角色
从1.3.0版本开始，JRaft开始引入Learner角色，该角色作为只读节点，比较类似follower角色。将从Leader复制日志并应用到本地状态机，但是不参与选举，
复制成功也不被认为是多数派的一员。简而言之，除了复制日志以外，只读成员不参与其他任何raft算法过程。一般应用在为某个服务创建一个只读服务的时候，
实现类似读写分离的效果，或者数据冷备等场景。

为一个Raft Group设置learner非常简单，任何一个以/learner为后缀的节点都将被认为是只读节点：
//一个3节点Raft Group带一个只读节点
Configuration conf = JRaftUtils.getConfiguration("localhost:8081,localhost:8082,localhost:8083,localhost:8084/learner");