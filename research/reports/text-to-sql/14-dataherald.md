# 14. Dataherald：Context Store、Golden SQL 与自适应 SQL Agent

## 项目卡片

- GitHub：[Dataherald/dataherald](https://github.com/Dataherald/dataherald)
- 本地源码：`research/source-code/agent-text2sql/14-dataherald`
- 锁定版本：`f8946182e6db`
- 定位：企业级 NL2SQL monorepo，含 Engine、Enterprise、Admin Console、Slackbot。
- 源码入口：`services/engine/dataherald/sql_generator/`、`context_store/`、`db_scanner/`、`repositories/`、`eval/`。
- 一句话判断：Dataherald 展示了 NL2SQL 如何从算法变成企业产品：数据库扫描、instructions、golden SQL、上下文向量库、生成/执行、自然语言回答、认证组织和可观测管理台。
- 重要性：P0/P1。

## STAR

### S — Situation

企业用户需要通过 API/管理台/Slack 问数据库，但数据库结构持续变化，不同组织有不同规则；开发团队还要追踪生成、执行和失败。仅有一个 prompt 函数无法满足运营。

### T — Task

Engine 提供数据库连接、Schema 扫描、context store、instructions、golden SQL、SQL generation/execution/evaluation 和自然语言答案；Enterprise 增加用户、组织与认证；Console 用于配置和观测。

### A — Action

`db_scanner` 扫描表列描述；repositories 将 connections、prompts、instructions、golden_sqls、sql_generations 等持久化。`context_store/default.py` 联合 Chroma/Pinecone/Astra 等向量存储检索相关上下文。`sql_generator/dataherald_sqlagent.py` 和 `adaptive_agent_executor.py` 组织生成与工具执行，记录 status；生成后可执行 SQL，再由 `generates_nl_answer.py` 将结果解释为自然语言。`eval_agent.py`/simple evaluator 支持质量评估。上层 Enterprise/Console 管理组织和运维。

### R — Result

形成完整的 NL2SQL 运营对象模型，尤其值得借鉴 instructions/golden SQL 与 generation history。风险是历史架构依赖多组件，Agent 执行器若权限过大可能执行危险查询，数据库扫描到的技术描述仍不足以表达业务指标。

## 完整执行流

1. 创建 database connection，扫描 Schema 和表列描述。
2. 管理员补充 instructions、字段含义和 golden SQL。
3. 上下文被向量化存入 context store。
4. 创建 prompt/sql_generation 记录并分配状态。
5. 检索相关 Schema、instruction、golden records。
6. SQL Agent 生成候选并通过 adaptive executor 调用允许工具。
7. 保存 SQL generation、错误和中间状态。
8. 授权时执行 SQL，得到结构化结果。
9. 可选生成自然语言回答；evaluator 记录质量。
10. Enterprise/Console 提供组织、认证、配置和可观测入口。

## 难点

| 难点 | 级别 | 源码设计 | SmileBoss 要求 |
|---|---|---|---|
| 上下文运营 | P1 | instructions/golden/context store | 建审批、版本和失效机制 |
| 状态追踪 | P1 | generation repositories/status | 保存每次候选、验证和执行摘要 |
| 企业隔离 | P0 | Enterprise organizations | 与现有 tenant/RBAC 融合，不能另建孤岛 |
| SQL 工具 | P0 | adaptive executor | 只暴露安全代理，不交底层连接对象 |
| 业务语义 | P1 | table descriptions | 与 WrenAI 式语义层结合 |

## SmileBoss 借鉴

MySQL 中建立 `semantic_instruction`、`golden_query`、`sql_generation_run`、`sql_candidate`、`sql_execution_audit`。管理员可把经确认的问题提升为 golden example；错误/过时示例立即失效并触发 Qdrant 删除。Console 应展示检索到的 Schema/规则/示例，而非只显示最终 SQL。

## 评分

架构学习 5/5；生产成熟度 4/5；招聘相关性 4/5；接入成本 2/5；运营数据模型的重要参考。

