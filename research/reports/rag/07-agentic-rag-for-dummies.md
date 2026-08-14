# 07. Agentic RAG for Dummies：父子块、混合召回、自纠错与评估

## 项目卡片

- GitHub：[NirDiamant/GenAI_Agents](https://github.com/NirDiamant/GenAI_Agents)
- 本地源码：`research/source-code/rag/07-agentic-rag-for-dummies`
- 锁定版本：`f4db3ddef0e2`
- 定位：从 PDF 解析到 Agentic RAG、RAGAS 和 Langfuse 的教学型全链路。
- 一句话判断：它提供了 SmileBoss 最值得直接吸收的通用 RAG 骨架：子块负责命中，父块负责理解，dense+sparse 负责召回，反思负责发现证据缺口。
- 重要性：P1。

## STAR

### S — Situation

小块检索精确但上下文不足，大块完整但向量表示被稀释；单一 dense 检索对编号、专有技能和关键词不稳定；一次生成无法识别自己缺了什么证据。

### T — Task

兼顾检索精度和回答上下文，融合语义与词法信号，允许查询澄清、多 Agent 并行研究、答案压缩和自我纠错，并用标准指标持续验证。

### A — Action

PDF 先转 Markdown；父块约 2000–4000 字符，子块约 500、重叠约 100。Qdrant 同时存 dense（示例采用 Qwen/Qwen3-Embedding-0.6B）与 sparse BM25 表示，查询先命中子块，再按 parent_id 回填父块。查询可先澄清和扩展；复杂任务分派给多个 Agent 并行检索，再 map-reduce 汇总。生成后判断证据是否充分，不足则重新检索；上下文过长时压缩。评估侧使用 RAGAS，观测侧接入 Langfuse。

### R — Result

该方案在通用性、解释性和可评估性之间平衡良好。代价是链路节点增多、延迟和调试复杂度上升；“自我纠错”若没有最大轮数会形成昂贵循环；压缩器也可能删除关键否定信息。

## 完整执行流

1. 文档解析为 Markdown，建立 document/parent/child 三层 ID。
2. 父块保留完整章节或经历，子块用于精确检索。
3. 为子块建立 dense+sparse 向量；父块原文存文档库或 Qdrant payload。
4. 查询理解判断是否含糊；必要时生成澄清问题或多查询。
5. 并行执行 dense、BM25 和元数据过滤检索。
6. 融合结果并去重，按 parent_id 回填完整上下文。
7. 重排、压缩并保留来源映射。
8. 简单问题直接生成；复杂问题由多个研究 Agent 分工，再汇总。
9. 反思节点检查“是否回答、证据是否足、是否矛盾”；失败则有限次重检索。
10. 输出答案和引用，记录 trace；离线运行 RAGAS 指标。

## 关键难点

| 难点 | 级别 | 项目做法 | 风险控制 |
|---|---|---|---|
| 粒度矛盾 | P1 | child search + parent retrieve | 父块过大仍会污染上下文，应设置 token 上限 |
| 词法与语义互补 | P1 | dense + sparse | 权重需用真实查询集校准，可用 RRF 降低标定负担 |
| Agent 循环 | P1/P2 | 反思重检索 | 最大轮数、token/时间预算、无新增证据即停止 |
| 上下文压缩 | P1 | compressor | 保存否定、数字、时间、主体和证据 ID |
| 评估 | P1 | RAGAS + Langfuse | 增加招聘领域人工金标和业务指标 |

## SmileBoss 借鉴

简历父块可对应一段完整工作经历，子块对应职责/成果/技能证据；职位父块对应岗位全文，子块对应职责、要求和福利。使用现有 Qdrant named vectors 或 dense+sparse 检索。反思节点只能问“还缺哪类证据”，不能让模型凭空改写候选人事实。推荐解释、完整度判断和面试评分必须携带 `evidence_span_id`。

## 评分

架构学习 5/5；生产成熟度 3/5；招聘相关性 4/5；接入成本 4/5；建议作为 SmileBoss 通用 RAG 主骨架。

