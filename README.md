# RentDetective（租房侦探）

基于 Spring Boot + LLM + RAG 的租房风险侦查 Agent。

## 简介

租房侦探通过 ReAct 循环驱动多工具协作，对房源描述、价格、相似房源、Prompt 注入等维度进行风险识别，输出带证据链的调查报告。

## 技术栈

- Java 17
- Spring Boot 3.3.2
- MyBatis-Plus
- OkHttp
- Jsoup
- MySQL
- Redis

## 当前状态

单模块 Spring Boot 项目骨架已搭好，核心接口和空类已定义，业务逻辑待实现。

已通过 `mvn clean compile` 编译验证。

## 包结构

```
io.github.shardkiht.rentdetective
├── llm/        LLM 引擎抽象层
├── agent/      ReAct 调查循环核心
├── rag/        向量检索层
├── semantic/   租房语义包（规则、工具、案例库）
├── eval/       评测框架
└── app/        启动层 + 接口层
```

依赖方向严格单向：`llm → agent → rag/semantic → eval → app`。

## 编译

```bash
mvn clean compile
```
