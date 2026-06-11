# Risk Register — 高危区地图与人类理解深度要求

> 运行模式依据: `docs/logs/plans/2026-06-11-ai-era-engineering-operating-model.cn.md` §6
> （风险定价深度函数）。本表是它的可寻址落地：高危区不停留在口头共识，
> 精确到包/文件；CODEOWNERS 的高危段与本表逐行对应，触及即触发主备双签 +
> PR 深审清单。
>
> 维护规则：新增高危路径 = 同一 PR 内更新本表 + CODEOWNERS + 对应深读导引；
> 三者不同步由评审拦截（本表是手写声明，按"声明须有奇偶校验"原则，
> CODEOWNERS 路径段就是它的校验对照物）。

## 深度等级定义

| 等级 | 含义 | 验收方式 |
|---|---|---|
| **L3 实现级** | 负责人能徒手修改、能独立评审 AI 对该区的修复、能向审计员解释根因 | 立约+深读冲刺出口考核 |
| L2 契约级 | 懂公开缝与承诺、会用证据准绳验收、会触发下钻调查 | 立约冲刺出口 |
| L1 行为级 | 只需懂行为承诺（同信任未读源码的第三方库） | 无考核要求 |

## 高危区清单（要求 L3）

| # | 高危区 | 精确路径 | 主/备 | 为什么高危 | 深读导引 |
|---|---|---|---|---|---|
| R1 | 租户认证与交叉校验 | `runtime/boot/JwtTenantValidator.java`、`runtime/boot/A2aTenantAuthFilter.java`、`service/starter/ServiceTenantAuthFilter.java` | A/B + C/D | 多租户隔离的第一道门；HS256 过渡路径（ADR-0164）的密钥纪律；alg-confusion 攻击面 | `docs/deep-dives/tenant-isolation.cn.md` |
| R2 | 路由授权（东西向能力签发） | `service/core/HmacRouteGrantService.java`、grant 头链路（`X-Ascend-*`） | C/D | 签名即授权；默认密钥防呆（WARN/拒启）是唯一防线；伪造 grant = 跨租户调用 | `docs/deep-dives/route-grants.cn.md` |
| R3 | Run 内核与幂等 | `runtime/run/`（DFA、CAS）、`runtime/idempotency/`、`engine/a2a/IdempotentRequestHandler.java` | A/B | 事务一致性的根基（不变量 I1–I5 全部立足于此）；错一处则重复执行/丢失执行 | `docs/deep-dives/run-kernel.cn.md` |
| R4 | A2A wire 契约 | `engine/a2a/A2aAgentExecutor.java`（事件路由、HITL 约定）、客户端 `A2aEvents`/`AscendA2aClient`（方言对称性） | A/B + E/C | 两端方言必须对称；SDK 已知缺陷史（isFinal 丢弃、cancel 不对称）的平台侧约定都埋在这里，改错即全链路静默失效 | `docs/deep-dives/a2a-wire-contracts.cn.md` |
| R5 | LLM 网关安全 | `llm/gateway/MintedTokenAuthenticator.java`、`ModelAliasRegistry`（启动校验）、`RestClientUpstreamModelClient`（凭证处理） | B/A | 铸造令牌 = 租户级模型凭证；上游 API key 绝不外漏（含 "Bearer null" 一类退化）；别名表错配 = 跨租户计费/越权 | `docs/deep-dives/llm-gateway-security.cn.md` |

## 非高危区的默认等级

- 一般业务逻辑、引擎适配器（openJiuwen/AgentScope/LangGraph）、bus 能力面：**L2**
- 胶水、示例、生成物（facts/graph）：**L1**

## 已知开放风险（深读导引核实时发现,待 owner 定级处置）

| # | 发现 | 位置 | 现状 |
|---|---|---|---|
| O1 | 无 `exp` claim 的令牌永不过期（`exp > 0 &&` 守卫跳过缺失值）,且无测试钉住 | `JwtTenantValidator.validate` | 过期纪律完全依赖签发侧;详见 R1 导引 |
| O2 | grant 头在接收端无代码强制校验——`/v1/route-grants/validate` 是目标方需主动调用的工具端点,"目标侧必须校验"目前是部署纪律 | 目标 runtime 侧（无对应 filter） | 详见 R2 导引;撤销链路（ROUTE_GRANT_REVOKED）亦为半成品 |
| O3 | resolve 端点的 body 租户绕过：`ServiceTenantAuthFilter` 只交叉核对头和查询参数,而 `ResolveGrantRequest.tenantId` 在 JSON body——持 tenant-a JWT 可请求签发 tenant-b 的 grant,纵深只剩租户作用域的目录解析 | `RouteGrantController` + `ServiceTenantAuthFilter` | 详见 R2 导引 |

## 已知的风险定价待复核项

- OIDC/JwtDecoder 生产路径落地时（ADR-0164），R1 的深读导引与考核内容需重写；
- Postgres 持久层落地时，R3 扩入存储一致性（RLS、迁移脚本进入 L3 范围）。
