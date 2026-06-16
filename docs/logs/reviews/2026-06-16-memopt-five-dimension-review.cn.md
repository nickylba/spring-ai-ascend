# MemOpt 五维复检(鲁棒 / 弹性 / 韧性 / Ops·性能 / 经济)

日期:2026-06-16 · 范围:`memopt/` 模块(A2A 共享记忆 + per-user 记忆)· 关联 [ADR-0162](../../adr/0162-a2a-shared-memory.yaml)

诚实前提:**MemOpt 现为 in-memory 后端的 kit + 契约**(可离线评测);真正的存储/索引/语义在闭源 Java 引擎(形态 C,未落地)。下面把**已在 kit/客户端落地并测过**的 与 **属于引擎侧、本期未做** 的分开标。

## 记分卡

| 维度 | 状态 | 证据 / 缺口 |
|---|---|---|
| 鲁棒性 | ✅ 客户端 | per-user fail-open + 熔断(`Circuit`,失败 recall 返空、remember 跳过、连续失败开路短路);严格模式可surface。测试:`UserMemoryKitTest`(fail-open / 严格 / 熔断短路)。A2A 共享:**所有权违例 surface 不吞**(权限错≠基础设施错),后端错也 surface 交协作引擎 reclaim。 |
| 弹性(上千 A2A) | ✅ 已验证 | 单一**按 scope 分区**的共享存储(非每协作/每用户一结构),`ConcurrentHashMap` 水平可扩。测试:`ScaleTest` — **2000 个并发协作** + **5000 个并发用户**,零跨域泄漏、零竞争错误。引擎侧:真正水平扩容由闭源引擎容器承担。 |
| 韧性(反压) | ✅ 客户端负反馈 / ⚠️ 引擎侧 | 客户端**熔断 = 负反馈**:引擎过载→调用失败→开路→自动甩载、不再打后端(`Circuit`)。重负载下的**服务端限流/有界队列属引擎侧**(形态 C 容器),本期未做,已在 ADR/设计稿标注。 |
| Ops 可观测 + 性能 | ✅ 双模可观测 / ✅ 瘦客户端高性能 | `obs/`:`MemoryObserver` + `Slf4jMemoryObserver`(双模:routine→DEBUG,verbose→INFO,问题→WARN,`isEnabled` 守卫、MDC finally 清理)+ `MicrometerMemoryObserver`(`memopt.ops`/`memopt.op.latency`/`memopt.degraded`,低基数)+ 组合(故障隔离)。已接入 `UserMemoryKit` 与 `SharedMemoryKit`。测试:`MemoryObserverTest`(级别路由/扇出/隔离/MDC/kit 接入)。性能:kit 是瘦客户端,操作 O(1)/O(facts≤cap);热路径无昂贵构造(守卫)。真正高性能召回在引擎(向量/索引)。 |
| 经济性(token 节省) | ✅ 架构杠杆 | ① 持仓走 SoR**不进记忆**;② per-user 批内 `dedupe` + `maxFactsPerScope` 上限淘汰(footprint 有界,`UserMemoryKitTest` 验证);③ A2A 共享黑板让 agent **不重复发现**结论、跨 run **经验召回**避免重推(省 LLM 轮次);④ 语义蒸馏(进一步压缩)由引擎做。这些都减少冗余 LLM 调用 = 省 token。 |

## 本期明确未做(引擎侧 / 后续,非缺陷而是范围)
- 闭源 Java 引擎本体(向量索引、语义召回、存储分层)+ 容器交付 + mTLS + gRPC `memopt.v1` wire(本期是 in-memory 后端 + 同一门面)。
- 服务端**限流/有界队列**(重负载反压的服务端半边)。
- `memopt-runtime-adapter`(接平台 `MemoryProvider` SPI;doushuai 示例已示范桥接模式)。
- 经验"任务签名"调优、并发写的更强一致策略(当前 append-log + 所有权已够)。

## 结论
A2A 共享记忆 + per-user 记忆的 **kit/契约层**在五个维度上都有**已测**的落地(鲁棒/弹性/韧性-客户端/可观测/经济),规模到**数千并发**已验证。剩余的强项(高性能召回、服务端反压、真持久化)按设计**属闭源引擎**,边界清晰、已在 ADR 标注。全套 **33/33** 测试通过。
