# 深读导引 R5 — LLM 网关安全

> 对应风险登记：`docs/governance/risk-register.md` R5。目标读者：需要对
> `agent-runtime/src/main/java/com/huawei/ascend/runtime/llm/gateway/` 达到 L3
> （实现级）理解的工程师。本文压缩你的阅读路径，不替代读码。

## 1. 设计意图

这个区域存在的根本原因（Authority: ADR-0160）：**平台与任何 LLM 之间原本没有网关**。
真实的 LLM 流量是第三方框架内部（openJiuwen、AgentScope）发出的 wire-level
OpenAI-compatible HTTP，任何进程内 Java seam 都看不到它。所以网关被造成一个
**wire-level 出口端点**（`POST /v1/chat/completions`，缓冲 + SSE 两条路径），采用方式
是 ModelSpec 间接：agent 的 `baseUrl` 指向网关，`apiKey` 字段装的是铸造令牌。

承重决策（每条都改变了代码的形状，逐条对照源码核实过）：

1. **凭证间接链**。agent 持有的只是 minted token（`agent-sdk` 侧
   `GatewayModelResolver` 把它塞进框架现成的 `apiKey` 字段，框架零改动）；真实
   provider key 只存在于 `LlmGatewayProperties.Upstream.apiKey`（服务端配置，可由
   Vault 解析）。两段凭证在 `ChatCompletionsController.forward()` 中完成交换：
   入站 bearer 换出 `(tenantId, agentId)` 身份，出站请求换入 `route.apiKey()`。
   agent YAML 永远见不到 provider key — 这是 R5 的全部意义。
2. **租户归因只来自令牌，绝不来自请求头**。`MintedTokenAuthenticator` 的 Javadoc
   明说 "no caller-supplied identity headers"：身份是服务端 `tokens` 目录的查表结果。
   调用方伪造 `X-Tenant-Id` 之类的头对网关无效，因为网关根本不读任何入站头
   （除 `Authorization`），也不向上游转发任何入站头。
3. **遥测唯一出口是 listener 链**（ADR-0160 决策 3）。GENERATION span 和 spend
   record 只能经 `LlmCallListener` → `GenerationSpanSink` / `SpendLog` 流出，由
   ArchUnit 边界测试钉死。成本数据走 span 属性和 spend ledger，**绝不上
   Prometheus 标签** — ARCHITECTURE.md §4 #57：`tenant_id` 是无界基数，meter 标签
   只允许有界词表（alias / provider / outcome / direction，见 `LlmGatewayMetrics`）。
4. **W2 成本只记录、不执法**（ADR-0160 决策 4）。`SpendRecordListener` 落账，
   pre-call budget check 仍是 design_only。读到"网关为什么不拒绝超预算调用"时，
   答案是：故意没做，不是漏了。
5. **Spring AI ChatClient 被刻意挡在门外**（ADR-0160 决策 2，延续 ADR-0002 的
   分阶段策略）。网关内部 seam 是薄端口 `UpstreamModelClient`；ChatClient 若来，
   将作为网关的客户端，而非网关的内脏。

## 2. 攻击面 / 失效面

| # | 攻击/失效方式 | 防线位置（file:behavior） |
|---|---|---|
| A1 | 无令牌/伪造令牌调用，偷跑 provider 配额或探测上游行为 | `ChatCompletionsController.chatCompletions()`：认证最先，`401` 在**接触任何上游之前**返回；`MintedTokenAuthenticator.authenticate()` 纯查表，空 token、非 Bearer scheme 一律 empty |
| A2 | "Bearer null" / "Bearer " 退化：上游 api-key 未配置时把空凭证拼上线 | 双层：`ModelAliasRegistry.validateAlias()` 启动期强制 `apiKey != null`（缺了直接拒启并点名属性）；`RestClientUpstreamModelClient.hasCredential()` 对 null/blank 凭证**整个省略** `Authorization` 头 — 空 api-key 是显式声明的 no-auth 上游语义（本地模型服务器），不是缺省 |
| A3 | 别名表错配（base-url 写错租户的上游、漏 key）在请求期才炸成无法解释的 500 | `ModelAliasRegistry` 构造期校验，错误消息含完整属性路径 `agent-runtime.llm.gateway.aliases.<alias>.base-url`；部署失败而非请求失败 |
| A4 | 调用方在请求体里夹带头部/身份信息影响归因 | `LlmCallContext` 只从 `principal`（令牌查表结果）和 `route`（别名查表结果）构造；请求体只允许两处变异：model 名替换 + 流式 `stream_options.include_usage` 注入（`forwardedBody()`） |
| A5 | 上游 5xx 错误体直接透传，泄露 provider 内部信息 | `relayUpstream()`：`status >= 500` 统一替换为合成的 `502 upstream_error` 体；4xx（限流/校验）原样透传以保留调用方重试语义 |
| A6 | 上游接受连接后永不应答，钉死 servlet 线程 | `RestClientUpstreamModelClient` 构造器：connect timeout（默认 5s）+ request timeout（默认 120s）。注意边界：**流式路径只约束到响应头**，SSE relay 本体不设界 — 故意的，LLM 流可以合法地长于任何固定上限 |
| A7 | 平台其他模块绕过网关直连 provider 或自行发 GENERATION 记录，打穿归因 | `LlmGatewayEmissionBoundaryArchTest`：`UpstreamModelClient` 只许 gateway 包依赖；`GenerationSpanSink` 只许 `gateway..` 子树依赖 |
| A8 | 某条 GENERATION 记录丢失 tenant，审计断链 | `GenerationSpanSink.GenerationSpan` 紧凑构造器：tenantId null/blank 直接 `IllegalArgumentException` — 无租户的 span **无法被构造** |
| A9 | listener 抛异常拖垮 LLM 调用本身 | `ChatCompletionsController.notifyBefore()/notifyAfter()`：逐个 catch `RuntimeException`，只 log 不传播 — 观察者永不影响主路径 |
| A10 | 上游不报 usage 时编造 token 数，污染账本 | `LlmTokenUsage.estimatedAbsent()`（零 token + estimated 标记）；`ModelAliasRegistry.costUsd()` 对 estimated usage 返回 null；`record()` 对 estimated usage 不计 token meter — "测得的零"与"未知"被严格区分 |

未设防、需知情的面：minted token 是明文配置项且作为 Map key 整串可见
（`LlmGatewayProperties.tokens`）；令牌轮换 = 改配置重启，无在线吊销。
网关端点不在 `A2aTenantAuthFilter` 的 JWT 链路上 — 它的认证是端点自带的。

## 3. 支撑不变量清单

全部测试类已逐一在源码树验证存在。

| 测试类 | 钉住的承诺（坏了会暴露什么） |
|---|---|
| `llm.gateway.MintedTokenAuthenticatorTest` | 令牌→身份查表正确；Bearer scheme 大小写/空白容忍；缺头、未知令牌、外来 scheme 全部拒绝。坏了 = 401 纪律或身份解析失真 |
| `llm.gateway.ModelAliasRegistryTest` | 缺 base-url / 缺 api-key 在构造期失败且**点名属性**；显式空 api-key 被接受为 no-auth 上游；unpriced/unknown/estimated 三种情形 cost 为 null。坏了 = A2/A3/A10 防线失效 |
| `llm.gateway.RestClientUpstreamModelClientTest` | （WireMock）缓冲与流式两条路径都发 `Bearer <key>`；no-auth 上游两条路径都**省略** Authorization 头；构造器正确接线两个 timeout。坏了 = "Bearer null" 回归或超时失守 |
| `llm.gateway.ChatCompletionsControllerTest` | 401/404 在任何上游接触前返回；上游 4xx 透传、5xx→502、IO 失败→502 且 listener 仍被通知；抛异常的 listener 不影响调用；SSE 字节逐块原样中继 + include_usage 注入；无 usage 流报 estimated 零 |
| `llm.gateway.LlmGatewayAutoConfigurationTest` | `enabled` 缺省/false 时整个表面退避；别名错配使**启动**失败；存在 `OpenTelemetry` bean 时选 OTel sink、部署自带 sink 时两个默认都让位 |
| `llm.gateway.LlmGatewayIntegrationTest` | 全链路（真 HTTP + WireMock 上游）：转发字节 byte-identical（除 model swap）；GENERATION 记录六属性 + tenant 齐全；成功调用追加 spend ledger 行 |
| `architecture.SpanTenantAttributeRequiredTest` | §4 #57 执行器（enforcer E44，ADR-0061 §5）：span 载荷无租户不可构造；OTel 桥真的把 `tenant.id` 写上线；**实现普查**测试 — 具体 sink 集合变了就失败，强迫执行器重指向而非静默空转 |
| `architecture.LlmGatewayEmissionBoundaryArchTest` | A7 防线：`UpstreamModelClient` 与 `GenerationSpanSink` 的依赖边界 |
| `llm.gateway.UsageExtractorTest` / `LlmGatewayMetricsTest` / `SpendRecordListenerTest` / `LlmSpanEmitterListenerTest` / `otel.OtelGenerationSpanSinkTest` | 用量两种字段方言、meter 标签词表、只记成功调用、unpriced 计数、span 回填时间戳 |

另：`examples/agent-runtime-a2a-llm-e2e` 里有 `LlmGatewayWireSeamArchTest`
钉住示例侧的 ModelSpec 间接不被绕开。

## 4. 已知陷阱史

本分支上可考的真实记录（git log + docs/logs，未发现更多，不杜撰）：

1. **首落地版三处裸奔，当天修复**（commit `22d1f976`，2026-06-11）。网关骨架
   （`4aae0fc7`）落地时：上游调用**无任何 timeout**（A6 完全敞开）；别名表
   **无启动校验**（错配在请求期才炸）；`GenerationSpan` **可以无租户构造**。
   `22d1f976` 一并补齐，并 "de-rot" 了两个架构守卫 — 其中
   `SpanTenantAttributeRequiredTest` 此前断言的不是真实发射 seam。教训：
   每个"防线"都有一段不存在的历史，评审 AI 产出时按 §2 表逐项点名验证。
2. **前任执行器的空转史**（docs/logs/reviews/2026-05-25 rc48/rc49 系列）。网关
   的前身约束 §4 #56 由 `LlmGatewayHookChainOnlyTest` 执行，曾被外部评审两轮
   抓到 vacuous（断言一个 design-only 壳子"保持是壳子"）。ADR-0160 明确以
   `LlmGatewayEmissionBoundaryArchTest` 取代它。现在
   `SpanTenantAttributeRequiredTest` 里那个"实现普查"测试就是对这段历史的
   结构性回应：守卫必须自证非空。
3. **RestClient 流式陷阱**（`RestClientUpstreamModelClient` 类注释，设计期发现）。
   Spring `RestClient` 在 exchange 回调返回时即关闭上游响应 — 对在另一线程上
   中继 chunk 的 servlet streaming body 来说太早。所以流式路径绕开 RestClient
   直用 JDK `java.net.http.HttpClient`。改这里前必须懂这条，否则"统一两条路径"
   的好心重构会让 SSE 静默截断。
4. **应用 ObjectMapper 污染风险**（`ChatCompletionsController` 字段注释）。网关
   故意 `new ObjectMapper()` 而不注入应用的 — 应用侧 Jackson 定制（命名策略、
   serialization feature）会破坏转发体的 byte fidelity。

## 5. 深读路径（约 0.5–1 天）

按序读，每个文件附提取目标。路径前缀
`agent-runtime/src/main/java/com/huawei/ascend/runtime/llm/gateway/`。

1. `docs/adr/0160-w2-llm-egress-gateway.yaml` — 四条决策 + consequences；
   弄清"为什么是 wire-level 而不是进程内 seam"。
2. `LlmGatewayProperties.java` — 配置即威胁模型：aliases / tokens 两张表，
   两个 timeout 的语义注释（流式只约束到响应头）。
3. `MintedTokenAuthenticator.java`（36 行）— 全部认证逻辑；注意它有多小，
   小到任何"顺手加点功能"都该被怀疑。
4. `ModelAliasRegistry.java` — 构造期校验 + `costUsd()` 的三种 null 情形 +
   `Route` 派生值；fail-fast 错误消息为什么点名完整属性路径。
5. `ChatCompletionsController.java`（357 行，核心，慢读）— 请求处理顺序
   （认证→校验→转发→计量），`forwardedBody()` 的两处合法变异，
   `relayUpstream()` 的 5xx→502 / 4xx 透传分叉，`streamingRelay()` 的
   finally 块（中断的流也记账），`notifyBefore/After` 的吞异常纪律。
6. `RestClientUpstreamModelClient.java` — `hasCredential()`（A2 防线），
   两条路径为什么用两个 HTTP client（陷阱史 #3）。
7. `UsageExtractor.java` — SSE 逐行扫描的 O(line) 设计；`estimatedAbsent()`
   何时出现。
8. `spi/` 整包 + `LlmSpanEmitterListener.java` + `SpendRecordListener.java` —
   GENERATION 六属性 + tenant 的记录结构、紧凑构造器防线、只记成功调用。
9. `LlmGatewayAutoConfiguration.java` — opt-in 总开关；OTel 嵌套配置类为什么
   必须 class-presence-guarded（optional 依赖 + bean 签名引用缺席类型会炸）。
10. 测试侧两份：`LlmGatewayIntegrationTest`（全链路承诺的可执行清单）、
    `architecture/SpanTenantAttributeRequiredTest` + `LlmGatewayEmissionBoundaryArchTest`
    （守卫的自证非空机制）。
11. 客户端半边：`agent-sdk/.../spec/model/GatewayModelResolver.java` +
    `docs/developer-handbook.cn.md` §6/配置表 — 间接链在 agent 侧如何闭合。

### 自检：改这三处会发生什么？

**Q1：把 `RestClientUpstreamModelClient.hasCredential()` 的判断从
`!isBlank()` 放松成 `!= null`，会发生什么？**
A：空字符串 api-key（no-auth 上游的显式声明）会变成把 `Authorization: Bearer `
（空凭证）拼上线 — 正是 R5 点名的 "Bearer null" 退化。
`bufferedExchangeOmitsAuthorizationHeaderForNoAuthUpstream` 和
`streamingOpenOmitsAuthorizationHeaderForNoAuthUpstream`（WireMock 断言头不存在）
会抓住它。同时注意 `ModelAliasRegistry` 仍会拦住 null key — 两层防线相互独立。

**Q2：在 `LlmGatewayMetrics.recordRequest()` 里加一个 `tenant_id` 标签，
方便按租户看成本，会发生什么？**
A：违反 §4 #57 — `tenant_id` 是无界基数，会撑爆 Prometheus 时序。架构答案是
按租户的成本问题由 spend ledger（`SpendLog`）和 GENERATION span 的 `tenant.id`
属性回答（span 是采样存储、属性不做聚合维度，所以不受基数规则约束）。
注意：当前没有 ArchUnit 测试直接拦 meter 标签 — 这一改动会**静默通过**测试，
只有评审能拦。这正是 R5 要求 L3 理解的原因之一。

**Q3：把 `ChatCompletionsController.relayUpstream()` 的 `status >= 500`
分支删掉，让 5xx 也原样透传，会发生什么？**
A：上游 provider 的内部错误体（可能含上游主机名、内部 trace、配额账户信息）
直接暴露给 agent；同时调用方失去"502 = 平台出口故障、可换路由重试"的语义。
`upstream500MapsTo502UpstreamErrorShape`（单测）和
`upstream500SurfacesAs502UpstreamError`（集成）双层拦截。反向追问也要会答：
为什么 4xx 不翻译？— 401/429/400 必须原样到达调用方，其重试/退避逻辑才成立。

**Q4（附加）：把 `SpendRecordListener.afterLlmInvocation()` 开头的
`if (!result.success()) return;` 删掉呢？**
A：失败调用（包括 502、传输中断）会以 estimated-zero usage 落进 spend ledger，
账本里出现大量零成本行，按 `(tenantId, agentId, modelAlias, day)` 的日汇总被
噪声污染。`SpendRecordListenerTest` 钉住"只记成功调用"。
