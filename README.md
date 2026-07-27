# RentDetective（租房侦探）

基于 Spring Boot + LLM + RAG 的租房风险侦查 Agent。

## 简介

租房侦探通过 ReAct 循环驱动多工具协作，对房源描述、价格、相似房源、Prompt 注入等维度进行风险识别，输出带证据链的调查报告。

## 技术栈

- Java 17 / Spring Boot 3.3.2
- MyBatis-Plus / MySQL / Redis
- OkHttp（LLM HTTP 调用）
- Ollama（本地推理 qwen3:1.7b + 嵌入 bge-m3）
- 百炼平台（云端备用 qwen3.7-plus）

## 当前进度

| 模块     | 状态                                                                              |
|----------|-----------------------------------------------------------------------------------|
| llm      | ✅ 已完成：Ollama / OpenAI-Compatible 双引擎 + 主备降级 + Embedding               |
| agent    | ✅ 已完成：ReAct 循环、括号计数 JSON 提取、工具超时兜底、forceConclude 置信度封顶 |
| rag      | ✅ 已完成：向量存储、余弦相似度检索、启动时自动嵌入                               |
| semantic | ✅ 已完成：16 条规则匹配器、规则引擎、价格提取、4 个 Agent 工具                   |
| app      | 🔧 骨架已搭：Controller / Service / Mapper / 评测框架定义完毕，业务串联待完善     |
| eval     | 🔧 框架已定义，评测用例待填充                                                     |

## 包结构

```
io.github.shardkiht.rentdetective
├── llm/
│   ├── api/          接口层（LLMClient、EmbeddingClient）
│   └── impl/         实现层（Ollama、OpenAI-Compatible、Fallback）
├── agent/
│   ├── loop/         ReAct 循环 + 常量
│   ├── report/       证据链报告
│   └── tool/         工具注册与调度（含超时）
├── rag/              向量检索（CaseVector、CosineSimilarity）
├── semantic/
│   ├── engine/       规则引擎 + 建议生成
│   ├── rule/matcher/ 16 条风险匹配器
│   ├── pricing/      价格提取
│   ├── tool/         Agent 工具实现
│   └── cases/        案例库
└── app/              启动层 + Controller + Service + Eval
```

依赖方向严格单向：`llm → agent → rag/semantic → app`。

## 编译 & 测试

```bash
mvn clean compile
mvn test
```

## 运行

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

前置条件：本地 Ollama 运行（qwen3:1.7b + bge-m3）、MySQL、Redis。
