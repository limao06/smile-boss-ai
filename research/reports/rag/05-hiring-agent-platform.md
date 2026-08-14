# 05. Hiring Agent Platform：LangGraph + pgvector 的分阶段岗位推荐

## 项目卡片

- GitHub：[alirezadir/hiring-agent](https://github.com/alirezadir/hiring-agent)
- 本地源码：`research/source-code/rag/05-hiring-agent-platform`
- 锁定版本：`cea636574cc3`
- 技术定位：FastAPI、LangGraph、Gemini 与 PostgreSQL/pgvector 的岗位匹配 Agent。
- 一句话判断：规模很小，但把“嵌入简历 → 向量找岗位 → LLM 重排”拆成显式图节点，正好展示招聘推荐的最小可行架构。
- 重要性：P1，适合学习阶段边界和观测字段，不适合直接作为生产基座。

## STAR 分析

### S — Situation

单次大模型提示同时承担解析、召回和排序时，无法知道错误发生在哪一步，也无法独立缓存或评估。岗位库扩大后，把所有岗位塞入提示词更不可行。

### T — Task

把岗位推荐拆成可观测的节点：先得到查询向量，再由数据库高效召回候选，最后用模型根据更丰富的上下文重排，并记录各阶段分数和延迟。

### A — Action

LangGraph 定义三个主要节点：`embed_resume` 生成简历向量，`search_jobs` 在 pgvector 中找近邻，`llm_rerank` 使用 Gemini 对候选重新排序。向量维度约 768，检索 top-3；状态在节点间携带简历、候选岗位和结果。流程额外记录 `vector_score`、`llm_rank` 和 latency，使向量召回与模型精排可以分别诊断。

### R — Result

得到一个清晰的、可插入更多节点的推荐骨架。其局限也很明显：top-3 召回过窄，单一自由文本向量难覆盖技能/地点/薪资/年限约束，LLM 重排缺少结构化 rubric，pgvector 实现与 SmileBoss 的 Qdrant 配置不同。

## 完整执行流程

1. API 接收候选人简历或画像。
2. 初始化 LangGraph 状态，包含请求 ID、原始文本和时间戳。
3. `embed_resume` 调用嵌入模型，失败时记录节点错误。
4. `search_jobs` 用向量查询 pgvector，得到 top-3 岗位及相似度。
5. `llm_rerank` 将简历和候选岗位交给 LLM，产生新名次与理由。
6. 合并向量分、LLM 名次和延迟，返回推荐结果。
7. 生产版本还应加入硬过滤、证据召回、风险检查和离线反馈。

## 难点与重要性

| 难点 | 级别 | 项目做法 | SmileBoss 应增强 |
|---|---|---|---|
| 阶段可诊断 | P1 | 图节点分离、记录分数/延迟 | 增加 trace_id、模型/索引版本、重试状态 |
| 召回率 | P1 | 单向量 top-3 | dense+BM25+规则召回到 50–100 |
| 硬约束 | P0/P1 | 基本缺失 | 租户、职位状态、地点、薪资、授权先过滤 |
| 精排稳定性 | P1 | LLM 自由重排 | JSON rubric、证据引用、缺失值与置信度 |
| 反馈闭环 | P1 | 未形成 | 曝光、点击、投递、面试、录用分别建标签 |

## 对 SmileBoss 的使用建议

- 直接借鉴：明确节点和共享状态、保留每阶段分数/延迟、失败点可重试。
- 技术替换：`pgvector/Gemini` 替换为项目现有 `Qdrant + Qwen text-embedding-v3 + Claude 主模型及既有 fallback`，其余流程不变。
- 图节点建议扩展为：`load_profile -> hard_filter -> build_queries -> hybrid_recall -> candidate_aggregate -> rule_score -> cross_rerank -> explanation -> policy_audit -> persist`。
- 不建议：top-3 后才重排；把岗位全文和简历全文直接拼接；将 LLM rank 覆盖所有硬规则。

## 评分

架构学习 3/5；生产成熟度 1/5；招聘相关性 5/5；接入成本 5/5；适合作为最小流程图，不适合作为完整实现。

