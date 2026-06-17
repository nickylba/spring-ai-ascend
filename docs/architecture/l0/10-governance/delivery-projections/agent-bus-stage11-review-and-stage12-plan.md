---
artifact_type: delivery_projection
version: agent-bus-stage11-review-and-stage12-plan
status: draft
source_commit: 4b32d394
source_stage11_plan: docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage10-review-and-stage11-plan.md
source_decision: docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-decision.md
target_module: agent-bus
---

# agent-bus Stage 11 评审与 Stage 12 计划

## 0. 结论

最新提交 `4b32d394` 可以作为 Stage 11 的阶段性成果接受：MI11-001（lease 续约改读注入 `EpochClock`）、MI11-002（`deliver` 非 lease 异常兜底为 skipped）、MI11-003（`runOnce` 仅入参非法 fail-fast、loop 传播）三个运行态裂缝全部落地，**136 tests green**，路径 B 不变。Stage 11 把 claim / lease / dispatcher worker / dispatch loop / 续约 / 异常 / 契约 / SQL contract 这条路径 B 的逻辑链走完，in-memory 替身下可观测、可测试。

诚实评审不再像 Stage 10→11 那样暴露「可在路径 B 内继续修的裂缝」——Stage 11 修完后，剩余缺口全部指向路径 B 的护栏（`decision §6.1`：不引入 JDBC driver）。因此 Stage 12 主轴经人类确认为**正式启动真实持久化（打破路径 B）**。

**切片 0 裁决已完成（4 项选型）**：

| 选型 | 裁决 | 说明 |
|---|---|---|
| DB 产品 | **Postgres** | L2 §7 DDL/SQL 草案零改动；`SKIP LOCKED` + `RETURNING` + 原生 RLS 齐全；aarch64 镜像可用。 |
| migration·adapter 归属 + JDBC 方式 | **agent-bus 自有 + Spring JDBC** | adapter + Flyway 都在 agent-bus 内（ownership 清晰）；用 `spring-boot-starter-jdbc` + `JdbcTemplate` + 注入 `DataSource`。**影响**：agent-bus 从「纯 Java + JDK」模块变为 **Spring 模块**（L1 physical §2 表述需更新）。 |
| transport（真实投递绑定） | **拆出 Stage 12，单独议** | 「基于 MQ」的诉求触及 C3 投递模型根本裁决（push vs pull；且 MQ 撞 `decision §3` 对 C4 的拒绝 + `§6.1` 禁 broker）。transport / 投递模型作为独立 H2/H3 议题，可能需 review packet 复议 C3 dispatcher-push 模型 / C3+broker hybrid（`decision §2` 预留路径）。**不阻塞持久化**。 |
| RLS | **启用 Postgres RLS 纵深防御** | 应用层 `WHERE tenant_id=?` 主路径 + DB 层兜底（R-C.c 硬隔离）。 |

裁决落位后 **Stage 12 范围收敛为「真实持久化」**：Postgres JDBC adapter（Spring JDBC）+ Flyway migration + Testcontainers 真实 SQL 验证 + RLS。原计划的「真实投递绑定骨架」从 Stage 12 移除，deferred（独立议题，Stage 13+ / review packet）。

简短判断：

- Stage 11 方向正确、收口干净，作为 Stage 11 成果接受。
- Stage 11 暴露的剩余裂缝（deliver 异常重投无 attemptCount 递增 / 退避、续约同步边界、真实 deliver 耗时驱动续约未端到端验证）**无法在路径 B 内闭环**——需真实运行时才能观测；其中与投递相关的（deliver 异常重投、续约耗时）随 transport 议题一并 deferred。
- Stage 12 是架构转折（`agent-bus` 首次引入 JDBC / Flyway），规模大于路径 B 前序阶段，但**不引入 MQ / broker**（transport 拆出）。

## 1. 本次提交审查

### 1.1 完成情况

本次提交（`4b32d394`，experimental，已 push fast-forward）完成：

- MI11-001 lease 续约触发时机：新增纯 Java `EpochClock` 端口；`ForwardingDispatcherWorker` 增带 `EpochClock` 的重载构造器（原 3 参内部用 `SYSTEM` 向后兼容）；`runOnce` 续约判断改 `remaining = leaseUntilMillisEpoch − clock.epochMillis()`，修复 Stage 10 的 bug（tick 入参使 `remaining` 恒等于 `duration`，自然 loop 下续约永不触发）。
- MI11-002 deliver 非 lease 异常兜底：`runOnce` deliver 阶段单独 try-catch `RuntimeException` → `skipped++`（record 留 DISPATCHING，lease 过期 reclaim 重投，不丢消息）；契约写入 `ForwardingDeliveryPort.deliver` javadoc + ICD。
- MI11-003 runOnce 异常契约：仅入参非法抛 `IllegalArgumentException`（fail-fast）；`ForwardingDispatchLoop.run` 传播 fail-fast 是正确语义，不加 loop 级吞没。
- 文档同步：L1 × 4 + L2 × 2 + ICD + yaml + decision.md §8 + plan §7 执行记录。

验收判断：

- MI11-001 / 002 / 003 全部收口，136 tests green（`AgentBusForwardingRuntimeContractTest` 36 个）。生产代码仍纯 Java，ArchUnit 纯度 green。
- **Stage 11 修完后，in-memory 路径下能再发现的运行态裂缝已基本穷尽**，剩余缺口全部需要真实 DB / transport 才能闭环。

### 1.2 诚实暴露的剩余缺口（需真实运行时才能闭环）

- **deliver 异常路径缺重投语义**：违约 deliver 抛异常 → skipped → 留 DISPATCHING → lease 过期 reclaim → 无 attemptCount 递增 / 退避 / 熔断。需真实 transport 才能在真实异常分布上决定重投策略——随 transport 议题 deferred。
- **续约是 deliver 前单次检查**：同步 worker 无「deliver 进行中持续续约」；超长同步 deliver 仍会丢 lease。需真实 transport 才能评估是否要超时熔断 / 异步化——随 transport 议题 deferred。
- **真实「deliver 耗时驱动续约」未端到端验证**：in-memory 下 deliver 瞬时，续约靠注入时钟覆盖逻辑路径。需真实 transport。

这三项随 transport 议题 deferred（不在 Stage 12）。Stage 12 聚焦真实持久化，把并发安全基石（claim / lease-guarded mutation / reclaim / renew）从 contract 落为可验证的真实 SQL 行为。

## 2. 当前修改意见

Stage 12 是架构转折。MI12 表列「正式启动真实持久化」的工作项，每项标注是否打破路径 B、是否需 H2/H3 裁决。**transport（原 MI12-005）已拆出 Stage 12**，独立议题。

| 编号 | 问题 | 严重度 | 证据 | 修改意见 |
|---|---|---|---|---|
| MI12-001 | **H2/H3 裁决：DB 产品 / migration·adapter 归属 / transport / RLS 四项选型**（**切片 0 已裁决**） | 高（架构转折，前置） | `decision.md §6.1` / `§8`；`agent-bus/pom.xml`；`forwarding-persistence.md §2 护栏` / `§8`。 | **裁决落位**：① DB = **Postgres**；② 归属 + JDBC = **agent-bus 自有 + Spring JDBC**（`spring-boot-starter-jdbc` + `JdbcTemplate` + 注入 `DataSource`）；③ transport = **拆出 Stage 12，单独议**（投递模型 push vs pull / 是否引 MQ；`decision §2` 预留 C3+broker hybrid 路径，可能需 review packet 复议）；④ RLS = **启用纵深防御**。扩展 `decision §4`（许可范围加 JDBC / Flyway / Spring JDBC）+ 修订 `§6.1`（不再「停端口接口」）；`§6.2` 始终不得项不变（仍禁 concrete broker / Task state / 跨 tenant fallback / payload body）。 |
| MI12-002 | **真实持久化 adapter 缺失**：`ForwardingOutboxPort` / `ForwardingOutboxClaimPort` / `ForwardingInboxPort` 当前唯一实现是 in-memory 替身；真实 claim 并发抢占、lease-guarded mutation、reclaim、续约只存在于 §7.1 / §7.2 SQL 草案。 | 高 | `ForwardingOutboxPort.java` / `ForwardingOutboxClaimPort.java`；`forwarding-persistence.md §7.1` / `§7.2`；in-memory `leaseGuardedMutate` 按 owner 裁决、不按 `lease_until` 过期。 | 实现 Postgres adapter（**Spring JDBC `JdbcTemplate` + 注入 `DataSource`**）：`ForwardingOutboxPort` + `ForwardingOutboxClaimPort`（+ inbox）。claim 走 §7.1 `UPDATE ... WHERE (...) FOR UPDATE SKIP LOCKED RETURNING *`；状态变更走 §7.2 `WHERE tenant_id=? AND message_id=? AND status='DISPATCHING' AND lease_owner=? AND lease_until > now`，0 行 → `ForwardingLeaseException`（分类 RECORD_NOT_FOUND / NO_LEASE / OWNER_MISMATCH / NOT_DISPATCHING）；renew / release 只对当前持有人生效；reclaim 走 `lease_until <= now` 分支。adapter 调 `ForwardingStateMachine` 校验迁移后再 persist；tenant 行级过滤显式 `tenant_id=?`（应用层，RLS 纵深防御）。**in-memory 替身保留**为 fast test double。 |
| MI12-003 | **Flyway migration 未落地**：`forwarding-persistence.md §7` DDL（含 MI9-006 CHECK）是「未执行」草稿；`§8` migration 归属「未定」。 | 高 | `forwarding-persistence.md §7`（`ck_outbox_*` / `ck_inbox_*`，标注「未执行」）/ `§8`。 | 把 §7 DDL 落地为 `agent-bus/src/main/resources/db/migration/V<n>__create_agent_bus_forwarding_outbox_inbox.sql`（归属 = agent-bus 自有）。含 MI9-006 全部条件 CHECK + `ix_outbox_claim_due` 索引。RLS migration：`ENABLE ROW LEVEL SECURITY` + `CREATE POLICY ... tenant_id = current_setting('app.tenant_id')`，顺序遵循 §7.3（先建表 → 再 RLS）。rollback 遵循 §8。 |
| MI12-004 | **真实 SQL 行为未验证**：`forwarding-persistence.md §5` 明确「renew-or-lose-the-ack 是 §7.2 SQL contract，不在 in-memory 断言……真实 adapter 落地后补 Testcontainers」。 | 高 | `forwarding-persistence.md §5` / `§7.2`；ContractTest（in-memory，无法断言 `lease_until > now`）。 | 用 Testcontainers（Postgres）新增集成测试验证：① 两 worker 并发 claim 同一批 → 无重复（`SKIP LOCKED`）；② stale worker ACK → 0 行 / `ForwardingLeaseException`（guard）；③ lease 过期 → 第二 worker reclaim；④ renew 后超 TTL 仍丢 ack（§7.2 编码）；⑤ record 不变量 CHECK 在 DB 层兜底；⑥ tenant 行级隔离。**风险**：Testcontainers 需 Docker daemon，本环境（NAS / aarch64）能否跑未验证（见 §6 退路）。 |
| MI12-006 | **agent-bus 依赖与 ArchUnit 护栏需更新**：`agent-bus/pom.xml` 仅 test 依赖；ContractTest 当前断言 production 源不含 `java.sql.` / `javax.sql.`，引入 JDBC adapter 会撞纯度规则。 | 高（与 MI12-002 同步） | `agent-bus/pom.xml`；ContractTest 纯度断言（`doesNotContain("java.sql.", "javax.sql.")`）；ArchUnit 纯度规则。 | pom 引入 `spring-boot-starter-jdbc` + Postgres JDBC driver + Flyway（production）+ Testcontainers Postgres（test）。ArchUnit 纯度规则**精确化**：production forwarding runtime 允许 JDBC / Flyway / Spring JDBC；**仍禁** concrete broker / MQ client（Kafka / RabbitMQ / RocketMQ / NATS）、Task execution state、跨 tenant fallback、payload body。纯度断言从「禁 `java.sql.`」改为「禁 concrete broker client + Task state」——护栏从「禁一切外部依赖」精确化为「禁 broker 绑定 + Task state 越权」，不放松 §6.2 始终不得项。 |

> **MI12-005（原真实投递绑定 HTTP 骨架）已拆出 Stage 12**：人类裁决 transport「基于 MQ」，但 MQ 撞 `decision §3`（拒绝 C4）+ `§6.1`（禁 broker），且诉求触及 C3 投递模型根本裁决（push vs pull）。transport / 投递模型作为独立 H2/H3 议题单独议（可能需 review packet 复议 C3 dispatcher-push / C3+broker hybrid，`decision §2` 预留路径），不进 Stage 12。Stage 12 不引入 MQ / broker client。

## 3. Stage 12 目标

Stage 12 的目标是**正式启动真实持久化，打破路径 B**：把 in-memory 替身 + contract / draft DDL 推进为真实 Postgres JDBC adapter（Spring JDBC）+ Flyway migration + Testcontainers 真实 SQL 验证 + RLS，使转发底座第一次拥有真实运行时的并发安全基石。这是从 `decision §6.1`「停在端口接口」到「真实数据库实现」的架构转折，H2/H3 已裁决扩展许可范围（切片 0）。**transport / 真实投递绑定不在 Stage 12**（独立议题）。

> 裁决落位（Postgres + agent-bus 自有 Spring JDBC + RLS；transport 拆出）→ 接 JDBC adapter（MI12-002）+ Flyway migration（MI12-003）+ Testcontainers 真实 SQL 验证（MI12-004），同步 pom 与 ArchUnit 护栏（MI12-006）。`decision §6.2` 始终不得项不变。

Stage 12 顺序：切片 1（依赖 + 护栏）→ 切片 2（JDBC adapter）→ 切片 3（Flyway）→ 切片 4（Testcontainers）→ 切片 5（文档）。agent-runtime 集成、真实投递绑定、deliver 异常重投策略均 deferred（transport 议题 + 集成议题，Stage 13+）。

## 4. Stage 12 开发切片

### 切片 0：MI12-001 H2/H3 裁决（已完成）

4 项选型已与人类逐项确认并落位（见 §0 裁决表）：Postgres + agent-bus 自有 Spring JDBC + transport 拆出单独议 + RLS 纵深防御。扩展 `decision §4` / 修订 `§6.1`；`§6.2` 不变。

### 切片 1：MI12-006 依赖引入 + ArchUnit 护栏更新（已完成）

- `agent-bus/pom.xml` 引入：`spring-boot-starter-jdbc`（production）+ Postgres JDBC driver（production）+ Flyway（production）+ Testcontainers Postgres（test）。
- ArchUnit 纯度规则精确化：允许 JDBC / Flyway / Spring JDBC；仍禁 concrete broker / MQ client、Task execution state、跨 tenant fallback、payload body。ContractTest 纯度断言从「禁 `java.sql.`」改为「禁 concrete broker client + Task state」。
- 护栏更新本身有 ArchUnit / 断言锁定。

DoD：pom 引入依赖编译通过；ArchUnit 精确化且有断言锁定；现有 136 tests 仍 green。

### 切片 2：MI12-002 真实持久化 JDBC adapter（Spring JdbcTemplate）（已完成）

实现 `ForwardingOutboxPort` + `ForwardingOutboxClaimPort`（+ inbox）的 Postgres adapter：

- claim 走 §7.1 `UPDATE ... WHERE (...) FOR UPDATE SKIP LOCKED RETURNING *`（tenant-scoped + due + 非终态 + lease 空闲/过期）。
- 状态变更走 §7.2 lease-owner guarded `WHERE`，0 行 → `ForwardingLeaseException`（按原因分类）。
- renew / release 只对当前持有人生效；reclaim 走 `lease_until <= now`。
- adapter 调 `ForwardingStateMachine` 校验迁移后再 persist；tenant 行级过滤显式 `tenant_id=?`（应用层 + RLS 纵深防御）。
- in-memory 替身保留为 fast test double。

DoD：adapter 实现 outbox / claim（+ inbox）端口，claim / lease-guarded mutation / reclaim / renew / release 全走真实 SQL；adapter 单测（Testcontainers，切片 4）green。

### 切片 3：MI12-003 Flyway migration + DDL 落地（已完成）

- `agent-bus/src/main/resources/db/migration/V<n>__create_agent_bus_forwarding_outbox_inbox.sql`（归属 = agent-bus 自有）。
- 含 MI9-006 全部条件 CHECK + `ix_outbox_claim_due` 索引。
- RLS migration：`ENABLE ROW LEVEL SECURITY` + `CREATE POLICY ... tenant_id = current_setting('app.tenant_id')`，顺序遵循 §7.3。

DoD：Flyway migration 可执行，DDL CHECK + 索引 + RLS 落地；与 §7 草案一致（差异在 L2 标注）。

### 切片 4：MI12-004 real-SQL 行为验证（已完成；embedded-postgres）

- 先探活 Docker daemon + Testcontainers 拉取 Postgres aarch64 镜像；不可行则走退路（embedded-postgres / 拆 CI）。
- 验证 6 类真实 SQL 行为：并发 claim 无重复 / lease guard / reclaim / renew-or-lose-ack / CHECK 兜底 / tenant 隔离。

DoD：Testcontainers 集成测试覆盖 6 类，green；兑现 §5「真实 adapter 落地后补 Testcontainers」承诺。

### 切片 5：文档同步（已完成）

- L1 README / development / process / physical（Stage 12 引用 + 真实持久化落地 + agent-bus 变 Spring 模块 + 测试数；physical §2 更新含 JDBC adapter / Flyway；§5.2 移除「真实 JDBC adapter / Flyway migration」项，**保留**「真实投递绑定 / MQ」为未决）。
- L2 `forwarding-persistence.md`（§2 护栏：路径 B → 真实接 Postgres；§5 in-memory vs JDBC 注更新；新增 §14 Stage 12 决策表）。
- ICD（routeHandle 解包边界 + transport deferred 记录）。
- yaml（`stage12_scope` + contract_tests）。
- `decision.md`（§4 许可范围扩展 / §6.1 修订 / §8 Stage 12 行）。
- 本 plan §7 执行记录填完整。

同步重点：Stage 12 落地真实持久化，**不**宣称 transport / 投递绑定 / agent-runtime 集成已落地（独立议题）。§6.2 始终不得项不变。

## 5. Stage 12 可接受结果

可以接受：

- MI12-001：4 项选型裁决落位（Postgres + Spring JDBC + RLS；transport 拆出），decision §4 / §6.1 更新。
- MI12-002：JDBC adapter（Spring JdbcTemplate）实现 outbox / claim（+ inbox），claim / lease-guarded mutation / reclaim / renew 走真实 SQL。
- MI12-003：Flyway migration 落地（DDL CHECK + 索引 + RLS）。
- MI12-004：Testcontainers 覆盖 6 类真实 SQL 行为。
- MI12-006：pom 引入 JDBC / Flyway / Testcontainers；ArchUnit 护栏精确化（允许 JDBC、禁 broker / Task state）。
- 现有 136 tests 保持 green；新增 Testcontainers 集成测试 + adapter 单测 green。

不能接受：

- 引入 concrete broker / MQ client（transport 已拆出 Stage 12；`§6.2` 始终不得）。
- JDBC adapter 绕过 §7.1 / §7.2 的 claim / lease-guarded SQL（并发安全基石缺失）。
- Testcontainers 验证缺失（真实 SQL 行为仍是 contract，没被任何测试锁住）。
- ArchUnit 护栏被整体放松（应为「精确化」而非「移除」）。
- 写 Task execution state；跨 tenant fallback；放 payload body（§6.2 始终不得）。

## 6. 给施工智能体的提示

这轮任务是架构转折，规模大于路径 B 前序阶段。顺序：切片 1（依赖 + 护栏）→ 切片 2（JDBC adapter）→ 切片 3（Flyway）→ 切片 4（Testcontainers）→ 切片 5（文档）。切片 0 裁决已完成。

关键约束：

- **不引入 MQ / broker client**：transport 已拆出 Stage 12（独立议题）。ArchUnit 护栏精确化（允许 JDBC / Flyway / Spring JDBC，禁 concrete broker / Task state），不是移除。
- **§6.2 始终不得项不动**：禁 concrete broker / MQ、Task execution state、跨 tenant fallback、payload body。
- **adapter 用 Spring JDBC**（人类裁决，非裸 JDBC）：`JdbcTemplate` + 注入 `DataSource`；agent-bus 引入 `spring-boot-starter-jdbc`，成为 Spring 模块（L1 physical §2 更新）。
- **in-memory 替身保留**：ContractTest 继续用 in-memory 锁逻辑契约（fast path）；JDBC adapter + Testcontainers 锁真实 SQL 行为。
- **agent-runtime 集成 / 真实投递绑定不在 Stage 12**：deferred（transport 议题 + 集成议题，Stage 13+）。

测试基线：当前 136 tests green。Stage 12 完成后保持 green + 新增 JDBC adapter 单测 + Testcontainers 集成测试。构建命令见 `build-env-maven-via-settings-xml`（system mvn + `~/.m2/settings.xml` + Red Hat JDK 21；agent-bus 单模块 `mvn -f agent-bus/pom.xml test -s ~/.m2/settings.xml` 绕过 agent-runtime/pom.xml 的 a2a-java-sdk version missing pre-existing 坑）。

**Testcontainers / Docker 环境风险**（诚实标注）：Testcontainers 需 Docker daemon，本环境（NAS / aarch64）能否跑未验证。切片 4 先探活 `docker info` / 镜像拉取；若不可行，退路：① embedded-postgres（`io.zonky.test:embedded-postgres`，aarch64 兼容性需验证）；② 真实 SQL 行为测试拆 CI，本地只跑 adapter 单测（mock JDBC）+ in-memory ContractTest。无论哪条退路，**真实 SQL 行为必须有某种验证手段**，不能退回「纯 contract、无测试」。

## 7. 执行记录

- 切片 0（MI12-001 H2/H3 裁决）已完成：4 项选型逐项与人类确认并落位 —— ① DB 产品 = **Postgres**（L2 §7 草案零改动、`SKIP LOCKED` + RLS 原生、aarch64 镜像可用）；② migration·adapter 归属 + JDBC 方式 = **agent-bus 自有 + Spring JDBC**（adapter + Flyway 在 agent-bus 内，`spring-boot-starter-jdbc` + `JdbcTemplate` + 注入 `DataSource`；agent-bus 从「纯 Java + JDK」变为 Spring 模块）；③ transport = **拆出 Stage 12，单独议**（人类提出「基于 MQ」诉求以获反压 / 降低接收方压力，但 MQ 撞 `decision §3` 对 C4 的拒绝 + `§6.1` 禁 broker，且诉求触及 C3 投递模型根本裁决——dispatcher-push 无消费方控速能力，真正反压需 consumer-pull / MQ；transport / 投递模型作为独立 H2/H3 议题单独议，可能需 review packet 复议 C3 dispatcher-push / C3+broker hybrid，`decision §2` 预留路径）；④ RLS = **启用 Postgres RLS 纵深防御**（应用层 `WHERE tenant_id=?` 主路径 + DB 层兜底）。
- 裁决落位使 Stage 12 范围从「真实持久化 + 投递绑定」收敛为**真实持久化**（Postgres JDBC adapter + Spring JDBC + Flyway + Testcontainers + RLS）；真实投递绑定 / MQ / deliver 异常重投策略 / 续约真实耗时验证均随 transport 议题 deferred（Stage 13+ / review packet）。
- 扩展 `decision §4`（许可范围加 JDBC / Flyway / Spring JDBC）+ 修订 `§6.1`（不再「停端口接口」）；`§6.2` 始终不得项不变。L2 `forwarding-persistence.md` §2 护栏更新（路径 B → 真实接 Postgres）+ 新增 §14 Stage 12 决策表。
- 切片 1（依赖 + 护栏）已完成：`pom.xml` 引入 `spring-boot-starter-jdbc` / `flyway-core` / `flyway-database-postgresql`(runtime) / `postgresql`(runtime) + `embedded-postgres`(+ `embedded-postgres-binaries-linux-arm64v8:16.2.0`, test)；ArchUnit 把 Spring/JDBC 圈进 `persistence.jdbc` 子包，`bus.forwarding..` 主体仍纯 Java（hikari/jackson/reactor/kafka/nats/servlet/netty 全局禁）。
- 切片 2（JDBC adapter）已完成：`JdbcForwardingOutbox`（含 claim/lease）/ `JdbcForwardingInbox` / `ForwardingSqlCodec`；claim §7.1 `FOR UPDATE SKIP LOCKED RETURNING`，lease-guarded §7.2 `WHERE`+0 行分类（RECORD_NOT_FOUND/NO_LEASE/OWNER_MISMATCH/NOT_DISPATCHING），reclaim / renew / release（过期语义 `lease_until=-1` 保留 `lease_owner` 使 CHECK-valid）。
- 切片 3（Flyway）已完成：`V1__create_agent_bus_forwarding_outbox_inbox.sql`（MI9-006 全部条件 CHECK + `ix_outbox_claim_due` 部分索引 + §7.3 RLS fail-closed）。
- 切片 4（real-SQL）已完成：`ForwardingJdbcIntegrationTest` 17 tests green。**载体用 Zonky embedded-postgres（PG 16.2 in-process），非 Testcontainers**——本环境 Docker daemon 经认证代理（HTTP 407）对所有 registry 不可达、host 无 sudo、无本地 PG（§6 风险预案的 embedded 退路实际启用，L2 §7.4 记录；adapter/migration 不依赖测试载体，生产可换回 Testcontainers）。覆盖 6 类 + migration + RLS（含 releaseLease 过期语义、stuck-holder reclaim、renew-or-lose-ack）。
- 切片 5（文档）已完成：L2 `forwarding-persistence.md` §5/§7/§7.4/§14、ICD-agent-bus-forwarding-runtime、yaml（`stage12_scope`）、decision §8、L1（README/development/process/physical/ARCHITECTURE/logical）同步——统一把「路径 B / 不引入 JDBC」过时断言更新为 Stage 12 落地态，补 embedded-postgres 决策与 153 tests green。
- **全量构建 153 tests green**（Stage 11 的 136 + real-SQL 17），ArchUnit 纯度 11 tests green。transport / 真实投递绑定 / agent-runtime 集成仍 deferred（Stage 13+ / review packet）。
