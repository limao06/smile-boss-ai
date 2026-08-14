# 09. RAG Research Agent Template：索引图、检索图与研究子图分离

## 项目卡片

- GitHub：[langchain-ai/rag-research-agent-template](https://github.com/langchain-ai/rag-research-agent-template)
- 本地源码：`research/source-code/rag/09-rag-research-agent-template`
- 锁定版本：`39f3ce6ad9ca`
- 定位：LangGraph 的生产模板，支持 Elasticsearch、Pinecone、MongoDB 等存储变体。
- 一句话判断：它最重要的贡献是把“写索引”和“在线回答”拆成不同图，再把复杂研究封装为子图，避免一个巨型 Agent 包揽全部职责。
- 重要性：P1。

## STAR

### S — Situation

索引、普通问答和深度研究的资源模式完全不同：索引需要批处理和幂等；普通查询要求低延迟；复杂查询需要规划、多次检索和汇总。混成一个流程会导致部署、重试和扩缩容都困难。

### T — Task

建立三个边界清晰的图：索引图负责文档变化，检索图负责路由与短答案，研究子图负责复杂任务；存储适配器可替换，状态契约保持一致。

### A — Action

索引图解析、切分、嵌入并写入配置的向量/搜索后端。在线图先路由查询：可直接回答、需要澄清、普通检索或进入 researcher。复杂查询先产出研究计划，再生成多个子查询并行检索，聚合证据后写答案。通过 LangGraph state、节点和条件边明确控制分支与循环，后端可按模板替换。

### R — Result

部署边界、失败恢复和测试范围更清晰：索引失败不阻塞已有问答，复杂研究不会拖慢简单查询。风险在于模板本身不是完整产品，权限、租户、数据删除、评估和领域提示仍需实现；多后端支持也会扩大维护面。

## 完整执行流

**索引流**：变更事件 -> 加载文档 -> 规范化/切块 -> 嵌入 -> upsert -> 记录索引版本。  
**查询流**：输入 -> 分类/澄清 -> 普通检索 -> 重排/生成 -> 引用返回。  
**研究流**：任务 -> 研究计划 -> 子问题 -> 并行检索 -> 证据合并 -> 缺口检查 -> 最终报告。

关键设计是 researcher 作为有输入/输出 Schema 的子图，而不是任意读写父图所有状态。这样可单独测试“简历深度分析”“岗位市场研究”或“面试复盘”。

## 难点

| 难点 | 级别 | 做法 | SmileBoss 要求 |
|---|---|---|---|
| 批处理与在线隔离 | P1 | index/retrieval graphs | 分队列、资源池和故障域 |
| 查询路由 | P1 | 条件边 | 记录路由理由，低置信度默认澄清 |
| 并行研究 | P2 | 多子查询并发 | 限制并发、去重和成本预算 |
| 状态契约 | P1 | typed state/subgraph | Java DTO 版本化，节点只写授权字段 |
| 后端替换 | P2 | storage variants | SmileBoss 固定 Qdrant，减少无谓抽象 |

## SmileBoss 借鉴

建立 `ResumeIndexGraph`、`JobIndexGraph`、`RecommendationGraph`、`InterviewGraph` 和 `AnalyticsResearchSubgraph`。索引图通过消息事件增量更新；在线推荐绝不现场解析 PDF。复杂候选报告才进入 research 子图，普通岗位匹配走低延迟路径。节点间用结构化 DTO，不传无限增长的自由文本 history。

## 评分

架构学习 5/5；生产成熟度 3/5；招聘相关性 3/5；接入成本 3/5；优先借鉴其流程边界而非具体存储代码。

