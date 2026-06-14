---
level: L1
module: agent-bus
view: development
status: draft
---

# agent-bus SPI 附录

## 1. 已接受 SPI 清单

| SPI / 类型 | 包 | 职责 | 契约来源 | 状态 |
|---|---|---|---|---|
| `IngressGateway` | `com.huawei.ascend.bus.spi.ingress` | C2S 入口路由 | `ingress-envelope.v1.yaml` | 已纳入 L1 |
| `IngressEnvelope` | `com.huawei.ascend.bus.spi.ingress` | C2S 请求 envelope | `ingress-envelope.v1.yaml` | 已纳入 L1 |
| `IngressResponse` | `com.huawei.ascend.bus.spi.ingress` | C2S 同步确认 envelope | `ingress-envelope.v1.yaml` | 已纳入 L1 |
| `S2cCallbackTransport` | `com.huawei.ascend.bus.spi.s2c` | S2C callback transport | `s2c-callback.v1.yaml` | 已纳入 L1 |
| `S2cCallbackEnvelope` | `com.huawei.ascend.bus.spi.s2c` | S2C 请求 envelope | `s2c-callback.v1.yaml` | 已纳入 L1，tenant 迁移待做 |
| `S2cCallbackResponse` | `com.huawei.ascend.bus.spi.s2c` | S2C 响应 envelope | `s2c-callback.v1.yaml` | 已纳入 L1 |
| `ReflectionEnvelopeRouter` | `com.huawei.ascend.bus.spi.s2c` | reflection envelope 路由 | `reflection-envelope.v1.yaml` | 已纳入 L1，payload 类型待决策 |
| `FederationGateway` | `com.huawei.ascend.bus.spi.federation` | 跨部署 federation 路由 | `federation-envelope.v1.yaml` | 已纳入 L1 |
| `EnginePort` | `com.huawei.ascend.bus.spi.engine` | service-engine 中立执行端口 | `engine-port.v1.yaml` | 已纳入 L1 |

## 2. Engine SPI 说明

`bus.spi.engine` 是中立边界 home。它的存在不表示 `agent-bus` 拥有 engine runtime。

职责拆分：

- `agent-service` 驱动 `EnginePort`。
- `agent-execution-engine` 实现执行能力。
- `agent-bus` 提供中立 SPI 类型位置，避免 service 和 engine 直接形成不合适的模块耦合。

## 3. S2C tenant 迁移说明

当前 `S2cCallbackEnvelope` 字段包括 `callbackId`、`serverRunId`、`capabilityRef`、`requestPayload`、`traceId`、`idempotencyKey`、`deadline`、`requestAttributes`。

目标态需要增加：

- `tenantId`

迁移规则：

- `tenantId` 必须是 required field。
- 空字符串必须拒绝。
- YAML 契约、Java record、测试、构造点、文档和模板必须同步。
- registry 绑定可以作为兼容路径保留，但不能替代 envelope 内的 tenant scope。

## 4. SPI 纯度

SPI 包应保持纯 Java：

- 可以依赖 `java.*`。
- 可以依赖同一 SPI 包或 `agent-bus` 内 sibling carrier type。
- 不应依赖 Spring、Reactor、Jackson、HTTP framework 或 broker runtime。
- 不应依赖 sibling module 的生产代码。

## 5. 待补测试

| SPI | 测试缺口 |
|---|---|
| `IngressEnvelope` | required fields、tenant、trace、request type、requestAttributes defensive copy。 |
| `IngressResponse` | rejected reason、accepted cursor、deferred status。 |
| `S2cCallbackEnvelope` | `tenantId` 迁移后的 required field 和兼容性。 |
| `FederationGateway` | broker-agnostic、只使用 ingress carrier type。 |
| `ReflectionEnvelopeRouter` | map payload schema validation 或 typed record。 |
| `EnginePort` | terminal event 唯一且最后发出。 |
