# 13. DB-GPT：Agentic 数据应用、AWEL 与 SQL/代码执行

## 项目卡片

- GitHub：[eosphoros-ai/DB-GPT](https://github.com/eosphoros-ai/DB-GPT)
- 本地源码：`research/source-code/agent-text2sql/13-db-gpt`
- 锁定版本：`1982cd11fbc2`
- 定位：面向数据库的 AI 原生应用平台，覆盖 datasource、Text-to-SQL、RAG、Agent、AWEL workflow、模型与代码沙箱。
- 一句话判断：DB-GPT 展示了从数据源发现到 SQL/代码执行、错误修复、图表和报告的完整产品链，但体量很大；SmileBoss 应抽取其工作流和执行治理思想。
- 重要性：P0/P1。

## STAR

### S — Situation

数据问答不止生成 SQL：还要选择数据源、理解 Schema、执行、处理错误、分析结果、画图和解释。多个步骤若无工作流状态，很难控制权限和失败恢复。

### T — Task

提供 datasource metadata、Text-to-SQL、SQL/代码执行、Agent 和 AWEL 编排，使复杂分析可以规划、执行、观察和修正，并支持本地/多模型。

### A — Action

系统注册数据源并扫描 Schema/metadata，知识/RAG 提供上下文。AWEL 将预处理、模型、工具和输出组成 workflow。Agent 先规划数据任务，Text2SQL 生成查询，经数据库工具执行；错误或空结果反馈进入修复循环，复杂分析可调用受控代码沙箱生成统计和图表。运行记录模型、数据源和节点输出。

### R — Result

能力全面，可支持从问答到数据应用。代价是部署和概念复杂、攻击面大；一个平台同时管理数据库凭据、模型和代码执行，安全要求极高。直接引入会与 SmileBoss 后端和模型网关重叠。

## 完整执行流

1. 注册 datasource，安全保存凭据并扫描 Schema。
2. 用户问题携带 identity 和 semantic scope。
3. Workflow/Agent 判断数据源和任务类型。
4. 检索 Schema、业务文档和相似 SQL。
5. 生成 SQL，执行 AST/权限/成本验证。
6. 只读执行；观察 error/empty/shape。
7. 有限次修复或请求澄清。
8. 可选代码沙箱对结果做二次分析/图表。
9. 生成解释，返回 SQL、数据和 trace。

## 难点

| 难点 | 级别 | 做法 | SmileBoss 决策 |
|---|---|---|---|
| 多能力编排 | P1 | AWEL | 用现有服务实现小型强类型 DAG |
| 数据源安全 | P0 | datasource layer | 凭据隔离、只读副本、列/行策略 |
| 执行修复 | P1 | observation loop | 不把敏感数据库错误完整回传模型 |
| 代码沙箱 | P0 | sandbox | 与主服务网络隔离、资源限制 |
| 平台复杂度 | P2 | 一体化 | 不整体引入，避免双平台治理 |

## SmileBoss 借鉴

把 Analytics Agent 拆成 `Intent -> Semantic Context -> SQL Plan -> SQL Guard -> Execute -> Result Validate -> Explain/Chart`。SQL 与代码执行分开授权，默认 Text-to-SQL 不具备代码执行能力。真实招聘业务写操作永远不由该 Agent 执行。

## 评分

架构学习 5/5；生产成熟度 4/5；招聘相关性 4/5；接入成本 1/5；用于全链路与执行治理参考。

