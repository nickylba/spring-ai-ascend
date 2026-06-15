# InMemory MemoryProvider 样例教程

跟着做完，你会启动一个常驻样例服务，通过 curl 写入一条长期记忆，再模拟一次用户输入，确认 OpenJiuwen ReActAgent 的真实模型输入里包含该记忆。

---

## 0. 你将验证什么

本样例验证长期记忆链路：

```text
curl 写入记忆
        ↓
InMemoryMemoryProvider 保存
        ↓
curl 发起用户输入
        ↓
OpenJiuwen memory rail 检索记忆
        ↓
ReActAgent 模型输入包含 Relevant memory
        ↓
MemoryProvider.save(...) 保存本轮用户输入和 assistant 输出
```

本样例不依赖外部服务，适合测试团队先验证 `MemoryProvider` 的基本接入形态。

---

## 1. 环境准备

在仓库根目录执行命令。需要：

- JDK 21
- curl
- 本地 18081 端口未被占用

Windows PowerShell 可以把下面的 `./mvnw` 换成 `./mvnw.cmd`。

---

## 2. Step 1 — 启动守护进程

```bash
./mvnw -f examples/agent-runtime-middleware-memory-inmemory/pom.xml spring-boot:run
```

看到 Spring Boot 启动完成后，不要关闭终端。服务地址：

```text
http://localhost:18081
```

---

## 3. Step 2 — 写入一条长期记忆

另开一个终端执行：

```bash
curl -s -X POST http://localhost:18081/sample/memory/remember \
  -H 'Content-Type: application/json' \
  -d '{"stateKey":"demo-user","text":"the user prefers green tea"}'
```

期望响应里能看到：

```json
{
  "stateKey": "demo-user",
  "records": [
    {
      "content": "the user prefers green tea"
    }
  ]
}
```

---

## 4. Step 3 — 发起一次用户请求

```bash
curl -s -X POST http://localhost:18081/sample/memory/ask \
  -H 'Content-Type: application/json' \
  -d '{"stateKey":"demo-user","text":"green tea"}'
```

期望响应：

- `rawResults` 中包含 `output=pong`。
- `modelMessages` 中包含 `the user prefers green tea`。
- `modelMessages` 中包含 `Relevant memory:`，说明 memory rail 在调用模型前完成了检索和注入。
- `records` 中包含本轮用户输入和 assistant 输出，说明执行后触发了 `MemoryProvider.save(...)`。

说明：本样例的 InMemory provider 使用简单字符串匹配，不做向量语义检索；因此 curl 示例使用 `green tea` 作为 query，确保测试团队能稳定观察到记忆注入。

---

## 5. Step 4 — 查看保存结果

```bash
curl -s 'http://localhost:18081/sample/memory/records?stateKey=demo-user'
```

你应该能看到三类记录：

1. 第 3 步手工写入的长期记忆。
2. 第 4 步的用户输入。
3. 第 4 步的 assistant 输出。

---

## 6. Step 5 — 看代码入口

关键代码在：

- `MemoryInMemoryApplication.java`
- `SampleMemoryOpenJiuwenHandler#setOpenJiuwenRailFactories(...)`
- `SampleMemoryOpenJiuwenHandler#memoryRail(...)`
- `InMemoryMemoryProvider`

用户侧要复用这个模式时，核心动作是：

```java
handler.setOpenJiuwenRailFactories(List.of(context -> handler.memoryRail(context, memoryProvider)));
```

---

## 7. 清理

在启动服务的终端按 `Ctrl+C` 停止进程。
