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
| `bus.spi.s2c` | S2C callback envelope、response、transport、reflection router | SPI 已存在，S2C 有测试，tenant 迁移待做 |
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
| `S2cCallbackEnvelopeLibraryTest` | S2C envelope 基础字段和 trace 校验 | 需要随 `tenantId` 迁移更新 |
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
- 未通知冲突方的 S2C tenant 迁移。
- 将 W2 workflow primitives 直接生成为运行时代码。

## 6. S2C tenant 迁移开发影响

迁移切片至少需要修改：

- `docs/contracts/s2c-callback.v1.yaml`
- `agent-bus/src/main/java/com/huawei/ascend/bus/spi/s2c/S2cCallbackEnvelope.java`
- `agent-bus/src/test/java/com/huawei/ascend/bus/spi/s2c/S2cCallbackEnvelopeLibraryTest.java`
- 构造 `S2cCallbackEnvelope` 的 service/runtime 代码或测试。
- 声称 `S2cCallbackEnvelope.tenant_id` 的 L1 文档和模板。

迁移前必须完成通知和 owner 确认。
