# 19. Dify：多知识库检索、融合重排与应用编排

## 项目卡片

- GitHub：[langgenius/dify](https://github.com/langgenius/dify)
- 本地源码：`research/source-code/rag/19-dify`
- 锁定版本：`e3b3165e9a8d`
- 定位：LLM 应用平台，含知识库、工作流、Agent、模型管理和多租户能力。
- 一句话判断：Dify 展示了 RAG 如何作为平台能力服务多个应用，尤其是多知识库并行检索、缓存、加权融合和模型重排；但 SmileBoss 已有业务后端，不宜整体迁移。
- 重要性：P1/P2。

## STAR

### S — Situation

一个应用可能同时访问简历、职位、政策、题库等多个知识库，不同库的检索模式和 embedding 不同。若串行搜索延迟高，直接合并分数又不可比；平台还要处理模型限流、缓存、审计和配置版本。

### T — Task

支持 semantic、full-text、hybrid、keyword 等模式，按 metadata 限制范围，并行检索多个数据集，融合/模型重排后交给工作流节点；同时管理模型、速率和日志。

### A — Action

知识库维护文档、分段、embedding 和索引。运行时 retrieval node 收集被授权的数据集，应用 metadata filter，并行召回。多路结果可按向量/关键词权重合并，或交由 rerank model 重排；embedding 查询有缓存，模型调用受速率限制。检索输出作为工作流变量进入后续 LLM/Agent 节点，并保存运行日志。

### R — Result

应用配置、模型和知识库的产品化程度高，多数据集检索链路值得参考。风险是通用工作流很难表达招聘决策的强类型状态；低代码配置错误可能导致越权或不可重复，平台规模也会与现有系统重复建设。

## 完整执行流

1. 应用运行携带用户、租户和 workflow version。
2. Retrieval node 解析配置的数据集和查询。
3. 服务端确认数据集访问权，应用 metadata filter。
4. 多数据集/多检索模式并行执行；查询 embedding 可缓存。
5. 对结果去重，执行 weighted fusion 或 rerank model。
6. 截断上下文，作为变量传给 LLM/Agent 节点。
7. 输出答案/结构化结果，并记录模型、token、延迟和节点日志。

## 难点

| 难点 | 级别 | 做法 | SmileBoss 落点 |
|---|---|---|---|
| 多知识库 | P1 | 并行检索 | Resume/Job/Question/Policy 分库路由 |
| 分数融合 | P1 | 权重或模型 rerank | 离线校准，保存原始分数 |
| 模型治理 | P2 | 限流、缓存、统一 provider | 沿用现有 Claude 主模型及 fallback 配置 |
| 低代码安全 | P0 | 平台权限 | 关键过滤固化在代码，配置不可覆盖 |
| 追踪 | P1 | workflow logs | 建节点级 run/event 表 |

## SmileBoss 借鉴

借鉴 retrieval node 的统一契约和多库并发，但在 Spring Boot 内实现领域工作流。模型路由与 fallback 要记录触发原因，避免不同模型悄然改变评分。HR 可以配置权重和题库范围，但租户、敏感字段和保留期限不可配置绕过。

## 评分

架构学习 4/5；生产成熟度 5/5；招聘相关性 3/5；接入成本 1/5；学习平台治理，不建议重建业务于 Dify 内。

