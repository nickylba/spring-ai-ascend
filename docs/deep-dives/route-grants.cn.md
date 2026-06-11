# 深读导引 — R2 路由授权（东西向能力签发）

> 对应风险注册表 `docs/governance/risk-register.md` R2。目标读者：需要对该区
> 达到 L3（实现级）理解的负责人 — 能徒手修改、能独立评审 AI 对该区的修复、
> 能向审计员解释根因。本文压缩你的阅读路径，不替代读码。

---

## 1. 设计意图

**一个 `RouteGrant` 是一张签名的路由能力票（capability）**：它证明服务
facade 在一个有界的有效窗口内，授权了*一个*源智能体经*一个* A2A 方法调用
*一个*目标智能体。权威定义在 `RouteGrantService` 接口的 javadoc（模块
`agent-service`，包 `com.huawei.ascend.service.spi.routing`），那里同时钉住
两条红线：

- **租户红线** — grant 在签发时嵌入租户，`validate` 必须把它与入站上下文
  重新核对；为某租户签发的 grant 绝不可对另一租户校验通过。
- **A2A-NO-REWRITE** — grant 只授权这一跳；A2A 载荷以不透明字节透传，
  途中从不解析、校验或改写。**推论：载荷不在签名范围内。**

形态上的承重决策（Authority: ADR-0161）：

1. grant 模型诞生于 examples 的原型（提案
   `docs/logs/reviews/2026-06-05-agent-examples-a2a-route-grant-and-telemetry-proposal.cn.md`），
   核心论断是 **"endpoint 泄漏本身不等于越权"** — 裸 endpoint 不能作为权限
   依据，授权边界必须落在 grant 和目标侧校验上。ADR-0161 把试验证明的缝
   提升为 `service.spi.routing` SPI，并要求参考实现与测试同一变更落地。
2. **agent-service 保持 Spring-free**（ADR-0161 owner 决策 Q1）：
   `HmacRouteGrantService` / `InMemoryRouteGrantCache` 是纯 JDK 参考实现；
   Spring 边缘（`A2aGatewayController`、`RouteGrantController`、
   `AgentServiceAutoConfiguration`）住在 `agent-service-starter`。
3. **共享密钥 HMAC-SHA256 是参考实现，不是终态**：一切都在
   `@ConditionalOnMissingBean` 之后，部署方贡献自己的 `RouteGrantService`
   bean 即可整体替换（开发者手册 §8 替换表）。

### 一个 grant 证明什么 / 不证明什么

| 证明 | 不证明 |
|---|---|
| facade 曾授权这条 (tenant, source, target, method) 路由 | **持有者身份** — 纯 bearer 语义，无持有证明，拿到头就能用 |
| 全部字段未被篡改（签名范围见 §2 A2） | **载荷完整性** — body 不签名（A2A-NO-REWRITE） |
| 签发时刻该路由可解析（mint 时调用 `AgentDirectory.resolveRoute`） | **目标侧已校验** — 校验是目标方的义务，见 §2 A5 的执行缺口 |
| 在 `[issuedAt, expiresAt)` 窗口内有效 | **未被重放** — 窗口内无 nonce/一次性约束（§2 A4） |

---

## 2. 攻击面 / 失效面

| # | 攻击/失效方式 | 防线在哪（file:behavior） |
|---|---|---|
| A1 | **默认密钥伪造**：`agent-service.route-grant-secret` 默认值是入库的公开字符串（`AgentServiceProperties.DEFAULT_ROUTE_GRANT_SECRET = "agent-service-local-route-grant-secret"`）；知道它的任何人可离线铸造任意租户的"合法" grant，即跨租户调用 | `AgentServiceAutoConfiguration#routeGrantService`：默认密钥 + JWT 关闭 → 启动 **WARN**；默认密钥 + `agent-service.access.jwt.enabled=true`（生产姿态信号）→ **fail-fast** 抛 `IllegalStateException` 拒绝启动。这是该攻击面的*唯一*防线 |
| A2 | **字段篡改**：改 `a2aEndpoint` 指向恶意运行时、扩 method、续 `expiresAt` | `HmacRouteGrantService#canonicalGrant`：签名覆盖全部 10 个字段 — grantId、tenantId、sourceAgentId、targetAgentId、targetRuntimeId、a2aEndpoint、allowedMethods（**排序后**逗号拼接，保证 Set 迭代序不影响签名）、policyVersion、issuedAt、expiresAt，管道符拼接；`#validate` 用 `MessageDigest.isEqual` 常量时间比较重算签名 |
| A3 | **跨租户使用**：tenant-a 的 grant 拿去 tenant-b 的调用 | `HmacRouteGrantService#validate`：嵌入租户与 `InboundA2aContext.tenantId()` 不等 → `TENANT_FORBIDDEN`。校验顺序由 javadoc 与测试钉死：expiry → tenant → source → target → method → **签名最后**。注意这意味着错误码对未认证的 grant 也会泄露哪个字段不匹配（低风险信息预言机）；接受路径永远要求签名通过 |
| A4 | **窗口内重放**：截获 `X-Ascend-*` 头后在 TTL 内重放 | 无 nonce/jti 一次性存储；缓解仅靠短 TTL — `A2aGatewayController#forwardA2a` 硬编码 `Duration.ofSeconds(60)`，`RouteGrantRequest` 紧凑构造器默认 1 分钟、拒绝零/负 TTL。`GatewayErrorCode.ROUTE_GRANT_REVOKED` 存在于枚举但**当前无任何代码签发它** — 撤销路径尚未实现（见 §4） |
| A5 | **目标侧不校验 = 整个模型空转**：grep 全仓，生产代码中读取 `X-Ascend-Route-Grant-Signature` 的只有写入方 `A2aGatewayController#grantStampedHeaders`；接收端运行时**没有**校验 grant 的 filter。`POST /v1/route-grants/validate`（`RouteGrantController`）是给目标方*主动*调用的工具端点 | 当前防线＝部署纪律：目标运行时必须自行调用 validate（e2e 示例 `RuntimeRegistryHttpE2eTest` 演示该调用）。评审 AI 改动时警惕任何"既然没人校验就删掉签名头"的简化 |
| A6 | **resolve 端点的 body 租户绕过**：`ServiceTenantAuthFilter` 只交叉核对 `X-Tenant-Id` 头和 `tenantId` **查询参数**（代码注释明确：`getParameter` 对非表单内容类型只解析查询串）；而 `RouteGrantController.ResolveGrantRequest` 的 `tenantId` 在 **JSON body** 里 — 持有 tenant-a JWT 的调用方可请求签发 tenant-b 的 grant | 当前无逐字段防线；纵深依赖：mint 时 `AgentDirectory.resolveRoute` 是租户作用域的（只解析到 tenant-b 自己的运行时），且 grant 不证明持有者身份。改动该控制器时必须考虑把 body 租户纳入交叉核对 |
| A7 | **规范串注入**：`canonicalGrant` 用管道符拼接、方法列表用逗号拼接，字段值只做 trim/非空校验（`RouteGrant` 紧凑构造器的 `required`），未限制字符集 — 含管道符的字段值理论上可构造规范串碰撞 | 当前无防线；现实暴露度低（agentId/tenantId 由平台侧配置产生），但自定义实现替换 `AgentDirectory` 后引入任意字符的 id 时此项升级为真实风险 |
| A8 | **TTL 边界二义**：到期瞬间必须判为已过期，且两处实现要一致 | `HmacRouteGrantService#validate`（`isBefore` 或 `equals` 二者任一即过期；注意该行调用了两次 `clock.instant()`，跨毫秒 tick 时两个子式看到的 now 可能不同 — 当前语义仍安全偏拒绝）与 `InMemoryRouteGrantCache#get`（`!expiresAt.isAfter(now)`，命中即驱逐）。两端边界语义由各自的 exactly-now 测试钉死 |

`X-Ascend-*` 头链路全景（端到端）：客户端 → `POST /v1/agents/{agentId}/a2a` →
`A2aGatewayController#forwardA2a` mint grant（TTL 60s）→
`#grantStampedHeaders` 在入站头副本上盖 `X-Ascend-Route-Grant-Id` /
`X-Ascend-Route-Grant-Signature` / `X-Ascend-Source-Agent` /
`X-Ascend-Tenant` 四个头 → 字节透传到目标运行时（目标*应当*回调 validate）→
响应侧 `#relayHeaders` 给客户端盖观测头 `X-Ascend-Runtime-Instance` /
`X-Ascend-Route-Grant-Id` / `X-Ascend-Route-Resolve-Ms` /
`X-Ascend-First-Byte-Ms` / `X-Ascend-Forward-Start-Ms`。注意请求侧是
`put` 覆盖写 — 入站请求里伪造的同名头会被 facade 的真值覆盖，这是链路的
一个隐性防线。

---

## 3. 支撑不变量清单

全部测试类已核实存在于源码树。

**`HmacRouteGrantServiceTest`**（`agent-service`）
- `resolveGrantSignsTenantScopedRouteToTargetRuntime` — mint 出的 grant 携带租户作用域路由且能通过自校验；坏掉说明签发与校验失去往返一致性。
- `validateRejectsExpiredAndMismatchedGrants` — 逐一钉死 `TENANT_FORBIDDEN` / `SOURCE_AGENT_FORBIDDEN` / `TARGET_AGENT_MISMATCH` / `A2A_METHOD_FORBIDDEN` / `ROUTE_GRANT_EXPIRED` 五个拒绝码；坏掉说明红线校验或错误码契约被改。
- `grantExpiringExactlyNowIsAlreadyExpired` — 到期瞬间偏拒绝的边界语义。
- `validateRejectsTamperedGrant` — 篡改 `a2aEndpoint` 后签名失效（`ROUTE_GRANT_INVALID`）；坏掉说明签名覆盖范围出现缺口。

**`InMemoryRouteGrantCacheTest`**（`agent-service`）
- `cacheReturnsUnexpiredGrantAndEvictsExpiredGrant` / `grantExpiringExactlyNowIsEvicted` — 缓存绝不交出过期 grant，命中即驱逐。
- `invalidateRemovesGrantForKey` / `invalidateByPolicyVersionRemovesStaleTenantGrants` — 单键失效与按租户 + policyVersion 的批量失效（撤销模型的调用方半边）。

**`A2aGatewayControllerTest`**（`agent-service-starter`）— 钉住"运行时是
第三方邻接"的转发纪律：
- `nonEnumRuntimeStatusIsRelayedVerbatimNotMappedToClient400` — 上游怪状态码原样转发，不嫁祸客户端。
- `malformedRuntimeContentTypeFallsBackInsteadOfFailingTheRequest` — 坏 content type 回退 `application/json`。
- `forwardStartHeaderCarriesTheForwardOffsetNotACopyOfFirstByte` — `X-Ascend-Forward-Start-Ms` 是真实转发起点偏移（历史缺陷回归钉，见 §4）。
- `postProcessingFailureClosesTheUpstreamStreamAndSurfacesAGatewayFault` — 中继失败时关闭上游流、记 `GATEWAY_FORWARD_FAILED`、以 502 而非 400 暴露。

**`AgentServiceAutoConfigurationTest`**（`agent-service-starter`）— 密钥纪律：
- `defaultRouteGrantSecretWarnsAtStartup` / `provisionedRouteGrantSecretStartsWithoutTheWarning` — WARN 恰在默认密钥时出现。
- `jwtEnabledWithTheDefaultRouteGrantSecretFailsStartup` — 生产姿态 + 公开密钥 = 拒启（A1 的唯一防线的回归钉）。
- `jwtFilterGuardsTheServiceRoutesWhenEnabled` — `/v1/route-grants*` 在 JWT 开启时落在 `ServiceTenantAuthFilter` 作用域内；`jwtEnabledWithoutSecretFailsStartup` — JWT 开但无密钥拒启。

**`RuntimeRegistryHttpE2eTest`**（examples/agent-runtime-a2a-llm-e2e）—
头链路端到端：转发侧捕获 `X-Ascend-Route-Grant-Id` 与响应侧回显一致，并
实际调用 `POST /v1/route-grants/validate`。

---

## 4. 已知陷阱史

均可在 `git log` / `docs/logs/` 复核，未发明任何条目。

1. **`X-Ascend-Forward-Start-Ms` 曾是 first-byte 的复制品**。加固提交
   `3fe64330`（"harden the A2A gateway edge"）把它改为真实的转发起点偏移，
   并同时引入：上游怪状态码原样转发（此前可能映射为客户端 400）、中继失败
   时关闭上游流防泄漏、以及 A1 的 WARN / fail-fast 密钥纪律。该提交是这片
   区域的"事故驱动加固"集大成者 — 评审 grant 区改动前先读它的提交信息。
2. **WARN 断言的并行污染**。`AgentServiceAutoConfigurationTest` 标注
   `@Isolated`，类注释言明：route-grant-secret 的 WARN 断言读 JVM 全局捕获
   输出（`OutputCaptureExtension`），其他类并发发出同样 WARN 会渗入
   no-warning 断言。同窗口的提交 `767bf6ec` 因相邻的 logback 全局状态竞态
   隔离了 boot 测试 — 这个家族缺陷（JVM 全局状态 vs 并行测试）在该区出现过
   两次。
3. **撤销是半成品**：`GatewayErrorCode.ROUTE_GRANT_REVOKED` 在枚举中存在，
   但 `HmacRouteGrantService#validate` 从不签发它 — `policyVersion` 恒为
   `DEFAULT_POLICY_VERSION = 1`，服务端没有撤销判定；只有调用方缓存的
   `invalidateByPolicyVersion` 这半边。2026-06-05 提案设计了完整的
   policyVersion 撤销模型，提升到 SPI 时只落地了缓存侧。
4. **`InMemoryRouteGrantCache` 没有生产消费方**：grep 全仓，它只被自己的
   测试引用。它是提案中"源运行时本地缓存 grant"角色的库构件，等待东西向
   直连场景接线 — 不要因为"没人用"删除它，也不要误以为 facade 路径上有
   grant 缓存（facade 每次转发都重新 mint）。
5. **`RouteCacheKey.from` 的多方法陷阱**：键的 method 取
   `allowedMethods().stream().findFirst()` — `Set` 无序，多方法 grant 的
   缓存键不确定。今天安全是因为 `resolveGrant` 用
   `Set.of(request.a2aMethod())` 只签单方法；给 grant 扩多方法支持时此处
   必炸。
6. **概念迁移史**：examples 原型（`cf824bc9`、`278298fe`）→ ADR-0161
   Stage 1 提升 SPI（`00ea8e3a`）→ Stage 2 Spring 边缘 starter
   （`c0112091`）→ 边缘加固（`3fe64330`）。examples 里残留的 gateway 代码
   已重接到提升后的 SPI（`1f78923b`），读旧提交时注意类的包路径变过。

---

## 5. 深读路径（约 0.5–1 天）

按序读，每项标注要提取什么。路径前缀：`agent-service/src/main/java/com/huawei/ascend/service/`
与 `agent-service-starter/src/main/java/com/huawei/ascend/service/starter/`。

1. `docs/adr/0161-agent-service-serviceization-stage0.yaml` — 为什么有这个
   SPI、Spring-free 决策、A2A-NO-REWRITE 是 fronting 的绑定契约。
2. `docs/logs/reviews/2026-06-05-agent-examples-a2a-route-grant-and-telemetry-proposal.cn.md`
   §4–§6 — grant 模型的原始论证：endpoint 泄漏 ≠ 越权、目标侧校验矩阵、
   policyVersion 撤销设计（对照 §4.3 看今天落地了多少）。
3. `spi/routing/RouteGrantService.java` — 接口 javadoc 的两条红线原文。
4. `spi/routing/{RouteGrant,RouteGrantRequest,InboundA2aContext,RouteCacheKey}.java`
   — 紧凑构造器的校验（trim/非空/TTL 默认 1 分钟/拒绝零负 TTL）；
   `RouteCacheKey.from` 的 `findFirst` 陷阱。
5. `core/HmacRouteGrantService.java` — `canonicalGrant` 字段序、validate 的
   校验顺序（签名最后）、`MessageDigest.isEqual`、到期边界的双重取钟。
6. `core/InMemoryRouteGrantCache.java` — 命中即驱逐、`remove(key, grant)`
   的条件移除防并发误删。
7. `AgentServiceAutoConfiguration.java` — 密钥纪律分支、所有
   `@ConditionalOnMissingBean` 缝、JWT filter 的 URL 作用域。
8. `A2aGatewayController.java` — `grantStampedHeaders`（覆盖写防伪造头）、
   硬编码 60s TTL、`relayHeaders` 观测头、流失败处置。
9. `RouteGrantController.java` + `ServiceTenantAuthFilter.java` — 对照看
   A6：filter 核对头与查询参数，resolve 的租户在 body。
10. 四个测试类（§3）+ `RuntimeRegistryHttpE2eTest` — 确认每条不变量的钉子
    长什么样。

### 自检 — "改这三处会发生什么"

**Q1：从 `canonicalGrant` 中去掉 `expiresAt` 字段，哪个测试会拦住你？**
答：**没有现存测试直接拦截** — `validateRejectsTamperedGrant` 只篡改
`a2aEndpoint`。后果：签名不再覆盖到期时间，持有者可改写 `expiresAt` 让
grant 永生（expiry 检查读的是 grant 自带声明值）。这正是 L3 要求的原因：
评审必须知道签名范围是逐字段枚举的，删一个字段 = 该字段降级为不设防声明。
修这类改动必须同步补 tamper 测试。

**Q2：把 validate 的到期判断简化为只用 `isBefore`（删掉 `equals` 子式），
会怎样？** 答：`grantExpiringExactlyNowIsAlreadyExpired` 立即失败。语义
后果：恰在 `expiresAt` 瞬间的 grant 变为有效，而
`InMemoryRouteGrantCache#get` 在同一瞬间已驱逐（`!isAfter`）— 服务端与
缓存对同一张票的存活判断出现分歧窗口。

**Q3：把 A1 的 fail-fast 降级为 WARN，会怎样？** 答：
`jwtEnabledWithTheDefaultRouteGrantSecretFailsStartup` 失败。后果：配置了
JWT 的生产姿态部署可以带着公开密钥启动 — 任何读过本仓库的人都能离线伪造
任意租户的 grant，整个东西向授权模型静默归零，且因为请求仍"带签名头"，
事后审计很难发现。这条防线之所以是 fail-fast 而非 WARN，正是因为失效模式
是*静默的*。

**Q4（附加）：让 `resolveGrant` 支持多方法（`Set.of(m1, m2)`），最先坏的
是哪里？** 答：`RouteCacheKey.from` 的 `findFirst` 使缓存键不确定（§4.5）；
其次 `canonicalGrant` 的方法排序保证签名稳定（这半边是对的）。改之前先修
缓存键。