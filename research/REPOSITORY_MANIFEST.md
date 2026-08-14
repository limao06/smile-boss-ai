# 40 个仓库清单与版本锁定

> 所有仓库均已完成浅克隆并验证工作树干净。短提交号用于保证报告与源码版本一致。

## A. RAG：20 个项目

| # | 项目 | 定位 | GitHub | 本地目录 | 分支 / 提交 |
|---:|---|---|---|---|---|
| 01 | Hiring Agent | 简历结构化、证据评分与公平性 | [fatmakahveci/hiring-agent](https://github.com/fatmakahveci/hiring-agent) | `source-code/rag/01-hiring-agent` | `main / 70fd3ea9aa74` |
| 02 | Resume Screening RAG | 多查询、RRF、small-to-big 简历召回 | [RanjitKumar48/Resume-Screening-RAG-Pipeline](https://github.com/RanjitKumar48/Resume-Screening-RAG-Pipeline) | `source-code/rag/02-resume-screening-rag` | `main / 7ebf3e6ca72c` |
| 03 | AWS Interview Assistant | 面试题知识库、元数据过滤、混合检索 | [aws-samples/sample-hr-assistant-with-rag](https://github.com/aws-samples/sample-hr-assistant-with-rag) | `source-code/rag/03-aws-interview-assistant` | `main / 46911edb0044` |
| 04 | SkillSyncer | 隐私脱敏、经历切分、职位匹配 | [Mightype/Multi-Agent-Resume-Screening](https://github.com/Mightype/Multi-Agent-Resume-Screening) | `source-code/rag/04-skillsyncer` | `main / 9e80ae137db6` |
| 05 | Hiring Agent Platform | LangGraph + pgvector 的岗位推荐流水线 | [alirezadir/hiring-agent](https://github.com/alirezadir/hiring-agent) | `source-code/rag/05-hiring-agent-platform` | `main / cea636574cc3` |
| 06 | Skillspace | FAISS 双塔匹配与离线评估 | [DmitriiKovalev/Skillspace](https://github.com/DmitriiKovalev/Skillspace) | `source-code/rag/06-skillspace` | `main / f46f5e6badf6` |
| 07 | Agentic RAG for Dummies | 父子块、稠密+稀疏、自纠错与 RAGAS | [NirDiamant/GenAI_Agents](https://github.com/NirDiamant/GenAI_Agents) | `source-code/rag/07-agentic-rag-for-dummies` | `main / f4db3ddef0e2` |
| 08 | Enhanced Agentic RAG | 结构感知切块、语义增强、重排、审计 | [FareedKhan-dev/Enhanced-Agentic-RAG](https://github.com/FareedKhan-dev/Enhanced-Agentic-RAG) | `source-code/rag/08-enhanced-agentic-rag` | `main / ae4cbb313b17` |
| 09 | RAG Research Agent Template | 索引图、检索图、研究子图 | [langchain-ai/rag-research-agent-template](https://github.com/langchain-ai/rag-research-agent-template) | `source-code/rag/09-rag-research-agent-template` | `main / 39f3ce6ad9ca` |
| 10 | Local Deep Researcher | 搜索—总结—反思循环 | [langchain-ai/local-deep-researcher](https://github.com/langchain-ai/local-deep-researcher) | `source-code/rag/10-local-deep-researcher` | `main / a53b13c7022b` |
| 11 | Microsoft GraphRAG | 知识图谱、社区报告、全局/局部检索 | [microsoft/graphrag](https://github.com/microsoft/graphrag) | `source-code/rag/11-microsoft-graphrag` | `main / e810e63ac143` |
| 12 | RAGFlow | 深度文档解析、融合召回与引用 | [infiniflow/ragflow](https://github.com/infiniflow/ragflow) | `source-code/rag/12-ragflow` | `main / 5e871986e259` |
| 13 | DeepSearcher | 路由、子查询、检索、反思与父块扩展 | [zilliztech/deep-searcher](https://github.com/zilliztech/deep-searcher) | `source-code/rag/13-deep-searcher` | `master / d89e37cdfbbe` |
| 14 | R2R | 多模态、混合检索、KG、引用与权限 | [SciPhi-AI/R2R](https://github.com/SciPhi-AI/R2R) | `source-code/rag/14-r2r` | `main / 9c5a94d151f9` |
| 15 | KAG | Chunk-Knowledge、Schema 与逻辑规划 | [OpenSPG/KAG](https://github.com/OpenSPG/KAG) | `source-code/rag/15-kag` | `master / fdab15b3929d` |
| 16 | Cognee | 向量+图谱+本体的长期记忆 | [topoteretes/cognee](https://github.com/topoteretes/cognee) | `source-code/rag/16-cognee` | `main / 4b9dd362625d` |
| 17 | Haystack | 模块化检索流水线与系统化评估 | [deepset-ai/haystack](https://github.com/deepset-ai/haystack) | `source-code/rag/17-haystack` | `main / ba92ec9de3be` |
| 18 | RAGs | 自然语言配置 RAG 流水线 | [vanna-ai/rags](https://github.com/vanna-ai/rags) | `source-code/rag/18-rags` | `main / 4bec27023950` |
| 19 | Dify | 多知识库检索、融合重排与应用编排 | [langgenius/dify](https://github.com/langgenius/dify) | `source-code/rag/19-dify` | `main / e3b3165e9a8d` |
| 20 | Onyx | 企业连接器、ACL 与混合 Agentic RAG | [onyx-dot-app/onyx](https://github.com/onyx-dot-app/onyx) | `source-code/rag/20-onyx` | `main / 26ff6e8835d3` |

## B. Agent 协作与写作：10 个项目

| # | 项目 | 定位 | GitHub | 本地目录 | 分支 / 提交 |
|---:|---|---|---|---|---|
| 01 | Microsoft Agent Framework | Agent 与工作流统一运行时 | [microsoft/agent-framework](https://github.com/microsoft/agent-framework) | `source-code/agent-text2sql/01-microsoft-agent-framework` | `main / ae7fa3389c8f` |
| 02 | LangGraph | 有状态图、检查点、HITL 与恢复 | [langchain-ai/langgraph](https://github.com/langchain-ai/langgraph) | `source-code/agent-text2sql/02-langgraph` | `main / 644815f9e5bc` |
| 03 | CrewAI | Crew 角色协作与 Flow 状态编排 | [crewAIInc/crewAI](https://github.com/crewAIInc/crewAI) | `source-code/agent-text2sql/03-crewai` | `main / 754d7323beb2` |
| 04 | AutoGen | 消息驱动 AgentChat/Core/Extensions | [microsoft/autogen](https://github.com/microsoft/autogen) | `source-code/agent-text2sql/04-autogen` | `main / 027ecf0a379b` |
| 05 | MetaGPT | SOP 驱动的软件团队与结构化产物 | [FoundationAgents/MetaGPT](https://github.com/FoundationAgents/MetaGPT) | `source-code/agent-text2sql/05-metagpt` | `main / 11cdf466d042` |
| 06 | ChatDev | YAML 工作流与虚拟软件公司 | [OpenBMB/ChatDev](https://github.com/OpenBMB/ChatDev) | `source-code/agent-text2sql/06-chatdev` | `main / 4fb2db0ea903` |
| 07 | CAMEL | Role-Playing、社会/Workforce 与工具生态 | [camel-ai/camel](https://github.com/camel-ai/camel) | `source-code/agent-text2sql/07-camel` | `master / 32fb32494897` |
| 08 | STORM | 多视角研究、访谈、提纲与长文写作 | [stanford-oval/storm](https://github.com/stanford-oval/storm) | `source-code/agent-text2sql/08-storm` | `main / fb951af7744d` |
| 09 | Open Deep Research | 监督者—研究员并行研究和报告生成 | [langchain-ai/open_deep_research](https://github.com/langchain-ai/open_deep_research) | `source-code/agent-text2sql/09-open-deep-research` | `main / 1b7d2e80db9f` |
| 10 | TaskWeaver | 面向数据分析的 Planner + Code Interpreter | [microsoft/TaskWeaver](https://github.com/microsoft/TaskWeaver) | `source-code/agent-text2sql/10-taskweaver` | `main / d44ddef23f90` |

## C. Text-to-SQL：10 个项目

| # | 项目 | 定位 | GitHub | 本地目录 | 分支 / 提交 |
|---:|---|---|---|---|---|
| 11 | Vanna | DDL/文档/历史 SQL 的示例 RAG | [vanna-ai/vanna](https://github.com/vanna-ai/vanna) | `source-code/agent-text2sql/11-vanna` | `main / 365d0617c1a4` |
| 12 | WrenAI | MDL 语义层与受治理的 Text-to-SQL | [Canner/WrenAI](https://github.com/Canner/WrenAI) | `source-code/agent-text2sql/12-wrenai` | `main / 7f7370e4e9b0` |
| 13 | DB-GPT | Agentic 数据应用、AWEL 与 SQL/代码执行 | [eosphoros-ai/DB-GPT](https://github.com/eosphoros-ai/DB-GPT) | `source-code/agent-text2sql/13-db-gpt` | `main / 1982cd11fbc2` |
| 14 | Dataherald | 企业 NL2SQL Engine + Enterprise + Console | [Dataherald/dataherald](https://github.com/Dataherald/dataherald) | `source-code/agent-text2sql/14-dataherald` | `main / f8946182e6db` |
| 15 | PremSQL | 本地优先的小模型 Text-to-SQL 工具链 | [Anindyadeep/text2sql](https://github.com/Anindyadeep/text2sql) | `source-code/agent-text2sql/15-premsql` | `main / b656dc04142b` |
| 16 | XiYan-SQL | 多生成器、M-Schema、修复与选择 | [XGenerationLab/XiYan-SQL](https://github.com/XGenerationLab/XiYan-SQL) | `source-code/agent-text2sql/16-xiyan-sql` | `main / 603dedac706d` |
| 17 | CHESS | IR/Schema/Candidate/Unit Test 四 Agent | [ShayanTalaei/CHESS](https://github.com/ShayanTalaei/CHESS) | `source-code/agent-text2sql/17-chess` | `main / 3d6e835f858d` |
| 18 | Chat2DB | Java 数据库客户端与 AI SQL 产品集成 | [codephiliax/Chat2DB](https://github.com/codephiliax/Chat2DB) | `source-code/agent-text2sql/18-chat2db` | `main / 5ee1e990e73f` |
| 19 | SQLCoder | 专用 Text-to-SQL 模型与推理基线 | [defog-ai/sqlcoder](https://github.com/defog-ai/sqlcoder) | `source-code/agent-text2sql/19-sqlcoder` | `main / de7249834e4f` |
| 20 | Text2SQL Eval Toolkit | 标准/Agentic SQL 评估和错误分析 | [IBM/text2sql-eval-toolkit](https://github.com/IBM/text2sql-eval-toolkit) | `source-code/agent-text2sql/20-text2sql-eval-toolkit` | `main / 60dd4515236a` |

## 完整性说明

- 仓库总数：40。
- 克隆策略：`--depth 1 --filter=blob:none`，降低磁盘占用并保留当前工作树。
- 工作树检查：40/40 均为 clean。
- Chat2DB 在 Windows 首次检出时遇到长路径限制；已仅对该仓库设置 `core.longpaths=true` 并恢复完整工作树。
- 源码研究入口优先顺序：README/官方文档 → 配置/示例 → 入口与状态结构 → 检索器/Agent/执行器 → 测试与评估。

