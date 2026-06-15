# Agent Runtime Memory InMemory Example

本样例验证“长期记忆 `MemoryProvider` 解耦”的最小接入方式。它启动一个常驻 Spring Boot 进程，用户通过 curl 写入记忆、发起一次用户输入，并从响应中观察记忆是否进入 OpenJiuwen ReActAgent 的真实模型输入。

完整 step by step 教程见：[TUTORIAL.cn.md](TUTORIAL.cn.md)。

## 快速启动

```bash
./mvnw -f examples/agent-runtime-middleware-memory-inmemory/pom.xml spring-boot:run
```

默认监听：

```text
http://localhost:18081
```

## 主要接口

| 接口 | 用途 |
|---|---|
| `POST /sample/memory/remember` | 写入一条长期记忆 |
| `POST /sample/memory/ask` | 模拟一次用户请求，并触发 memory rail 检索和注入 |
| `GET /sample/memory/records?stateKey=demo-user` | 查看当前 `stateKey` 下保存的记忆 |

## 设计要点

- 样例通过 `SampleMemoryOpenJiuwenHandler#setOpenJiuwenRailFactories(...)` 预设 OpenJiuwen rail。
- 业务方可以按同样方式把 `memoryRuntimeRail(context, provider)` 设置到自己的 OpenJiuwen handler。
- `InMemoryMemoryProvider` 只放在 example 中，用于端到端验证；生产环境应替换成企业自己的长期记忆服务。
