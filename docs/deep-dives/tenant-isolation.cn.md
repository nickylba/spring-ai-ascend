# 深读导引 R1 — 租户认证与交叉校验

> 对应风险区：`docs/governance/risk-register.md` R1。目标读者：需要达到 L3
> （实现级）理解的负责人 — 徒手改、独立评审 AI 修复、向审计员讲清根因。
> 本文压缩你的阅读路径，不替代读码。预计 0.5–1 天。

## 1. 设计意图

**为什么是"交叉校验"而不是"JWT 取代 header"。** ADR-0040 裁决了这一点：
W0 客户端只发 `X-Tenant-Id` 头；W1 在其上**叠加** JWT 校验（additive
hardening），`tenant_id` claim 必须与显式声明的租户归属一致 — 不一致
`403`，令牌缺失/无效 `401`，`X-Tenant-Id` **不被移除**。直接替换会在没有
弃用窗口的情况下打断全部 W0 客户端。这就是为什么两个过滤器的逻辑都是
"先验令牌、再核对显式归属、最后发布认证租户"，而不是"只认令牌"。

**为什么是手写 HS256 而不是 Spring Security `JwtDecoder`。** ADR-0164 把
`JwtTenantValidator` 的 HS256 共享密钥方案记录为**过渡路径**：开发/本地与
单运营方部署，密钥带外预置，无需任何密钥基础设施，且两个入口边缘语义
完全一致、无 IdP 即可全量测试。生产方向是 OIDC/JWKS 经 `JwtDecoder`
（Nimbus），平台只保留 `tenant_id` 交叉校验。不现在迁移的硬理由：
`spring-security-oauth2-resource-server` 在 agent-runtime 是 **optional**
依赖，而校验器被 agent-service-starter 消费 — 迁移会让 Nimbus 变成每个
facade 部署的硬传递依赖，只为重新实现 HS256；且没有可验证 IdP 的 OIDC
路径等于"以安全边界名义交付的未测试代码"。

**为什么只有一个校验器。** `JwtTenantValidator`（`runtime.boot`）被运行时
`A2aTenantAuthFilter` 和服务边缘 `ServiceTenantAuthFilter` 共用 — 一个
校验器、一套语义、一套安全测试钉住两个边缘。其公开表面刻意收窄为两个
成员：`validate(String) -> ValidatedToken | InvalidTokenException`，这就是
未来 `JwtDecoder` 实现的替换缝。

**JWT 禁用时（header-attribution-only 模式）为何可接受、在哪可接受。**
两个过滤器都由 `@ConditionalOnProperty(... jwt.enabled=true)` 注册，
默认不注册 — 此时租户归属退化为 `X-Tenant-Id` 头（或配置的
`default-tenant-id`）。这是 W0 行为，适用于：本地开发、网络边界已由
外部基础设施控制的单租户部署。平台对此有一道防呆联动：
`AgentServiceAutoConfiguration` 在 `agent-service.access.jwt.enabled=true`
但 `route-grant-secret` 仍为入库默认值时**启动即抛**
`IllegalStateException`（配置了 JWT = 生产姿态，可伪造的路由授权不可
共存）；JWT 禁用 + 默认密钥则只 WARN — 这条 WARN 就是
"header-only 模式只许待在本地开发"的运行时提示。

## 2. 攻击面 / 失效面

| # | 攻击 / 失效方式 | 防线位置（file:behavior） |
|---|---|---|
| 1 | **alg confusion**：客户端送 `alg: none` / `RS256` / `HS384` 的令牌 | `JwtTenantValidator.validate`：解析 header 后**立即**拒绝非 `HS256`，攻击者选择的 alg 永远到不了验签逻辑。注意：当前实现里 `verifySignature` 无条件算 HmacSHA256，所以即使删掉这个检查 `alg=none` 今天也会因签名不符而失败 — 该检查的真正价值是**纵深防御**，保护未来按 header alg 分派的 `JwtDecoder` 迁移 |
| 2 | **签名比较计时侧信道** | `JwtTenantValidator.verifySignature`：用 `MessageDigest.isEqual`（常量时间），不是 `Arrays.equals` |
| 3 | **过期令牌重放** | `validate`：`exp + clockSkewSeconds`（默认 30s，只向过期方向放宽）。**埋着的尸体**：`exp` claim 缺失或为 0 时令牌**永不过期**（`exp > 0` 守卫）— 签发侧必须始终带 `exp`，校验侧不强制 |
| 4 | **header/claim 错配**：以 bank-7 的令牌声明 `X-Tenant-Id: bank-9` | 两个过滤器的 cross-check 分支 → `403`。比较前对 header 做 `trim()` |
| 5 | **查询参数错配**（仅服务边缘）：`GET /v1/agents?tenantId=bank-9` + bank-7 令牌 | `ServiceTenantAuthFilter`：对 `request.getParameter("tenantId")` 做同样的交叉校验。控制器（`RuntimeRegistryController` 等）信任该参数，这道校验是唯一防线 |
| 6 | **JSON-RPC body 内自报租户**（`params.tenant` / `metadata.tenantId`） | `A2aAgentExecutor.metadata`：传输层租户（call-context state）优先于 `ctx.getTenant()` 和 metadata；而 `A2aJsonRpcController.serverContext` **总是**填充传输租户（JWT > header > `defaultTenantId`，默认值 `"default"` 非空），所以 HTTP 路径上 body 自报永远不生效 |
| 7 | **空密钥 / 弱密钥** | `JwtTenantValidator` 构造器对 blank 抛 `IllegalArgumentException`。**无最小长度检查** — 1 字符密钥会被静默接受，密钥强度纪律完全在部署侧 |
| 8 | **过滤器顺序错乱** | `RuntimeAutoConfiguration`：`TraceParentFilter` order 5 在前、租户认证 order 10 在后 — 认证拒绝也携带可关联的 `trace_id` |
| 9 | **路径覆盖缺口** | 运行时：`shouldNotFilter` 放过非 `/a2a` 前缀 + 注册时 `addUrlPatterns("/a2a","/a2a/*")` 双重限定（agent card 等公开端点刻意不设防）。服务边缘：只守 `GuardedPathPrefixes` 三个前缀（registrations/agents/route-grants） |
| 10 | **错误信息泄露** | `InvalidTokenException` javadoc 写明"message is safe to surface"；`reject()` 手拼 JSON 并把 `"` 换成 `'` 防注入 |
| 11 | **form-encoded body 被吃掉** | `ServiceTenantAuthFilter` 代码注释明示：`getParameter` 对非 form content-type 只解析查询串，JSON body 不受影响。但若客户端送 `application/x-www-form-urlencoded`，`getParameter` 会消费 body — 此边缘的路由都是 JSON/查询参数风格，注释是这一假设的存档 |

## 3. 支撑不变量清单

全部存在于源码树（已逐一核实）：

**`A2aTenantAuthFilterTest`**（`agent-runtime/src/test/java/com/huawei/ascend/runtime/boot/`）

| 测试方法 | 断了说明什么坏了 |
|---|---|
| `missingBearerTokenIsRejected401` | 匿名请求被放进了 `/a2a` |
| `badSignatureIsRejected401` | 验签被绕过 — 任何人可铸造租户身份 |
| `expiredTokenIsRejected401` | 过期重放窗口被打开 |
| `headerClaimMismatchIsRejected403` | **ADR-0040 交叉校验失守** — header 声明与认证身份可分裂 |
| `validTokenPublishesAuthenticatedTenantAndProceeds` | 认证租户没发布到 `AUTHENTICATED_TENANT_ATTRIBUTE`，下游退回 header 信任 |
| `validTokenWithoutHeaderProceedsWithClaimTenant` | claim 单独归属失效（无 header 客户端被错拒或错归属） |
| `nonHs256AlgorithmIsRejected` | alg-confusion 纵深防线被拆 |
| `nonA2aPathsAreNotFiltered` | 公开端点（agent card）被误设防 |

**`ServiceTenantAuthFilterTest`**（`agent-service-starter/src/test/java/com/huawei/ascend/service/starter/`）

| 测试方法 | 断了说明什么坏了 |
|---|---|
| `missingBearerTokenIsRejected401` / `badSignatureIsRejected401` / `expiredTokenIsRejected401` | 服务边缘三个守卫前缀（agents / registrations / route-grants 各取其一作请求路径）失守 |
| `headerClaimMismatchIsRejected403` | 服务边缘 header 交叉校验失守 |
| `queryParameterClaimMismatchIsRejected403` | **服务边缘特有**：`tenantId` 查询参数交叉校验失守 — 持他租户令牌即可枚举本租户目录 |
| `validTokenWithMatchingAttributionsProceeds` | header+param+claim 三者一致时被误拒 |
| `nonHs256AlgorithmIsRejected401` | 同上 alg 防线 |
| `unguardedPathsAreNotFiltered` | `GUARDED_PATH_PREFIXES` 范围意外扩大 |

两个测试类各自手工构造 JWS（`jwt()` 助手用 JDK `Mac` 签名）— 测试不依赖
被测代码的签名实现，是真正的独立验证者。

## 4. 已知陷阱史

- **M5 — 传输身份在 HTTP 边界被丢弃（2026-06-10 核心模块评审，已修复）。**
  `docs/logs/reviews/2026-06-10-core-modules-code-review.en.md` M5：修复前
  `A2aJsonRpcController` 创建 `new ServerCallContext(null, Map.of(), Set.of())`
  — 空 state，header 完全不传播；唯一幸存的租户身份是客户端**自报**的
  `params.tenant`，且示例配置宣传的 `agent-runtime.access.a2a.default-tenant-id`
  没有任何代码绑定（以为跑在 `sample-tenant` 下的请求实际跑在字面量
  `"default"` 下）。修复（commit `6c40441f`）：新增 `RuntimeAccessProperties`
  绑定该键；控制器把传输租户写入 call-context state；executor 让传输身份
  优先于自报身份。**教训：归属优先级链的每一环都要有绑定测试，
  "配置键存在"不等于"配置键被读"。**
- **JWT 认证本身是后补的（commit `5ee27d30`）。** W1 才落地 ADR-0040
  交叉校验；在此之前 `/a2a` 完全是 header 信任。理解这段历史可解释为何
  控制器优先级链里 header 路径仍然完整保留。
- **PR-179 评审质疑手写 JWS（2026-06-11，处置为 ADR-0164）。** 评审指出
  agent-runtime 已带 `spring-boot-starter-oauth2-resource-server`（optional）
  却手写验签。处置不是迁移，而是把 HS256 记录为显式过渡路径（理由见 §1），
  并在 javadoc 与开发者手册（`docs/developer-handbook.cn.md` §7）写明。
  **改此区前先读 ADR-0164 — "换成 Nimbus" 是被明确否决过的方案。**

## 5. 深读路径（按序，约 0.5 天）

1. `agent-runtime/.../runtime/boot/RuntimeAccessProperties.java` — 配置面：
   `agent-runtime.access.a2a.{default-tenant-id,jwt.enabled,jwt.hmac-secret,jwt.clock-skew-seconds}`；记住 `clockSkewSeconds` 默认 30。
2. `agent-runtime/.../runtime/boot/JwtTenantValidator.java` — 核心 100 行逐行读：
   alg 检查在验签**之前**、`MessageDigest.isEqual`、`exp > 0` 守卫、
   blank-secret 构造器防御、`ValidatedToken`/`InvalidTokenException` 两成员替换缝。
3. `agent-runtime/.../runtime/boot/A2aTenantAuthFilter.java` — `shouldNotFilter`
   前缀判定、`Bearer ` 的大小写不敏感 `regionMatches`、cross-check 分支、
   `AUTHENTICATED_TENANT_ATTRIBUTE` 的发布。
4. `agent-runtime/.../runtime/boot/RuntimeAutoConfiguration.java`（filter 注册段）—
   `@ConditionalOnProperty` 开关语义、order 5/10 关系、URL pattern 双重限定。
5. `agent-runtime/.../runtime/boot/A2aJsonRpcController.java` 的
   `serverContext` + `authenticatedTenant` — 归属优先级第一段：JWT > header >
   `defaultTenantId`，写入 `TENANT_STATE_KEY`。
6. `agent-runtime/.../runtime/engine/a2a/A2aAgentExecutor.java` 的
   `metadata(ctx, TENANT_STATE_KEY, ...)` — 优先级第二段：transport state >
   `ctx.getTenant()` > metadata > `"default"`；再看
   `IdempotentRequestHandler`（去重键 `(tenant, messageId)`）确认归属错误的
   爆炸半径：跨租户幂等碰撞。
7. `agent-service-starter/.../ServiceTenantAuthFilter.java` +
   `AgentServiceProperties.java`（`Access.Jwt` 块）— 与运行时边缘的差异点
   只有两个：`GUARDED_PATH_PREFIXES` 与 `tenantId` 查询参数交叉校验。
8. `agent-service-starter/.../AgentServiceAutoConfiguration.java` —
   `serviceTenantAuthFilter` 注册 + route-grant 默认密钥的 WARN/fail-fast 联动。
9. 两个测试类（§3）— 注意测试自带独立 `jwt()` 签名助手。
10. ADR-0040（`docs/adr/0040-...yaml`，交叉校验模型 + 禁用措辞清单）、
    ADR-0164（过渡路径 + 不迁移的三条理由）、手册 §7。

### 自查：改这三处会发生什么

**Q1：把 `verifySignature` 里的 `MessageDigest.isEqual` 换成
`Arrays.equals`，哪个测试会红？**
答：**没有测试会红** — 功能完全等价，这正是危险所在。`Arrays.equals`
逐字节短路比较，泄露"前 N 字节正确"的计时信号，理论上可逐字节猜出合法
HMAC。这是只能靠评审与本导引钉住的不变量（也是 AI 修复最容易"顺手
简化"掉的点）。

**Q2：把 `A2aJsonRpcController.serverContext` 改回
`new ServerCallContext(null, Map.of(), Set.of())`，会发生什么？**
答：M5 缺陷原样复活：executor 的 transport-state 分支拿不到值，回落到
`ctx.getTenant()` / `metadata.tenantId` — 客户端在 JSON-RPC body 里自报
什么租户就是什么租户，幂等键、会话记忆、Run 归属全部跟着错。
`A2aTenantAuthFilterTest` **不会**捕获（它只测过滤器），暴露这一点的是
控制器/集成层测试 — 改 §5 第 5 步的代码时必须连带跑 boot 包全部测试。

**Q3：从 `ServiceTenantAuthFilter.doFilterInternal` 删掉
`request.getParameter("tenantId")` 交叉校验段，会发生什么？**
答：`queryParameterClaimMismatchIsRejected403` 立即红。若连测试一起删：
持 bank-7 合法令牌的客户端可 `GET /v1/agents?tenantId=bank-9` 枚举他
租户的智能体目录、解析其路由 — 因为 `RuntimeRegistryController` 直接信任
该参数。运行时边缘没有等价物（`/a2a` 不用查询参数传租户），所以这是
**服务边缘独有**且唯一的防线。
