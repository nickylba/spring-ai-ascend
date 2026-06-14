---
level: L1
module: agent-bus
view: development
status: draft
---

# agent-bus 开发视图

## 1. 代码结构

当前 `agent-bus` 的开发结构以 SPI 包为中心：

```text
agent-bus/
  module-metadata.yaml
  pom.xml
  src/main/java/com/huawei/ascend/bus/spi/
    engine/
    federation/
    ingress/
    s2c/
  src/test/java/com/huawei/ascend/bus/spi/
    engine/
    s2c/
```

## 2. 包职责

| 包 | 职责 | 成熟度 |
|---|---|---|
| `bus.spi.ingress` | C2S 入口 envelope、response、gateway | SPI 已存在，测试待补 |
| `bus.spi.s2c` | S2C callback envelope、response、transport、reflection router | SPI 已存在，S2C tenant 已迁移，runtime 构造点待后续波次 |
| `bus.spi.federation` | 跨网络 federation gateway | SPI 已存在，运行时实现待定 |
| `bus.spi.engine` | service-engine 中立执行边界和相关基础类型 | SPI 已存在，被 engine/service 消费 |

## 3. 依赖规则

`agent-bus` 生产代码允许：

- Java 标准库。
- `agent-bus` 内部 sibling SPI 类型。

`agent-bus` 生产代码禁止：

- 依赖 `agent-service`。
- 依赖 `agent-execution-engine`。
- 依赖 `agent-client`。
- 依赖 `agent-middleware`。
- 依赖 `agent-evolve`。
- 在 SPI 包中引入 Spring、Reactor、Jackson、HTTP framework 或 broker runtime。

## 4. 测试现状

| 测试 | 覆盖 | 缺口 |
|---|---|---|
| `S2cCallbackEnvelopeLibraryTest` | S2C envelope 基础字段和 trace 校验 | tenantId required-field harness 已补齐 |
| `SuspendSignalTest` / engine 相关测试 | engine/suspend 基础语义 | 需要确认 terminal event harness |
| ingress 测试 | 暂缺 | 需要补 required fields、trace、tenant、response status |
| federation 测试 | 暂缺 | 需要补 broker-agnostic 和 ingress carrier type |
| reflection 测试 | 暂缺 | 需要决定 map validator 或 typed record |

## 5. 生成物边界

允许生成：

- L1 graph model。
- schema fixture。
- contract test skeleton。
- drift check manifest。

禁止自动生成：

- 运行时 broker 实现。
- 修改 production dependency graph 的代码。
- 未经 owner 裁决的 breaking 契约变更（如 S2C v1 这种 pre-GA 内部契约的字段增删，MI-005 方案 A）。
- 将 W2 workflow primitives 直接生成为运行时代码。

## 6. S2C tenant 迁移结果与剩余影响

Stage 2 已完成的迁移（commit `d894f494`）：

- `docs/contracts/s2c-callback.v1.yaml`：`tenant_id` 加入 request required fields（第七个必填字段，Rule R-C.c）。
- `agent-bus/src/main/java/com/huawei/ascend/bus/spi/s2c/S2cCallbackEnvelope.java`：新增 `tenantId` 组件，compact constructor 校验非 null、非 blank。
- `agent-bus/src/test/java/com/huawei/ascend/bus/spi/s2c/S2cCallbackEnvelopeLibraryTest.java`：补齐 null/blank `tenantId` 负向用例与既有构造点更新。
- `contract-catalog.md` / `contract-catalog.md.j2` / `spi-appendix.md`：preferred fix 升级为 migrated fact。

仍待后续波次补齐（不改 Task lifecycle 所有权，S2C-TENANT-006）：

- 构造 `S2cCallbackEnvelope` 的 `agent-service` / runtime 侧构造点。
- runtime-side schema validation integration。
- downstream 文档与治理模板的剩余同步。

迁移前已通知所有冲突方（CN-001..CN-007）；本迁移不改变 `agent-service` 对 Task lifecycle 的所有权。
