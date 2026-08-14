# 08. Enhanced Agentic RAG：结构感知切块、语义增强与审计 Agent

## 项目卡片

- GitHub：[FareedKhan-dev/Enhanced-Agentic-RAG](https://github.com/FareedKhan-dev/Enhanced-Agentic-RAG)
- 本地源码：`research/source-code/rag/08-enhanced-agentic-rag`
- 锁定版本：`ae4cbb313b17`
- 定位：Unstructured、Qdrant、SQLite、CrossEncoder 与多专家 Agent 的增强 RAG。
- 一句话判断：它展示了“让块在入库时变得更可检索”和“让独立审计者质疑答案”两条很实用的增强路线。
- 重要性：P1；安全审计为 P0。

## STAR

### S — Situation

表格、标题和段落具有不同结构，统一字符切分会破坏语义；用户查询的说法可能不会直接出现在原文中。即使检索到相关内容，生成 Agent 也可能选择性引用或做出越界结论。

### T — Task

在索引阶段保留结构并为每块生成丰富的检索代理信息；查询阶段先宽召回再交叉编码重排；生成阶段由规划者、专家、门卫和审计者分担职责。

### A — Action

Unstructured `chunk_by_title` 按标题组织段落，表格作为原子对象。每个块可生成摘要、关键词、假设问题，表格额外生成表意摘要；原文与增强文本关联存入 Qdrant，元数据/状态放 SQLite。查询先取 top-20，再用 CrossEncoder 精排 top-5。多 Agent 中 specialist 负责领域回答，gatekeeper 判断是否满足条件，planner 分解任务，auditor 检查证据和风险；另有 red-team 思路测试注入和不可信内容。

### R — Result

对短、标题模糊或表格型块的召回更强，CrossEncoder 比单向量更准确，独立审计降低“自己验证自己”的盲点。代价是索引时额外 LLM 成本、增强文本可能引入幻觉、top-20 重排耗时，以及 SQLite 在高并发生产环境的伸缩限制。

## 完整执行流

1. 解析文档元素，识别 title/text/table/list。
2. 按标题聚合，表格不拆碎；生成稳定 chunk_id。
3. 对块生成摘要、关键词、假设用户问题和表格说明。
4. 原文、增强字段、来源和版本共同入索引；增强内容标记为模型生成。
5. planner 识别任务并选择专家/检索范围。
6. dense/元数据检索 top-20。
7. CrossEncoder 对 query-document pair 打分，取 top-5。
8. specialist 基于证据生成草案。
9. gatekeeper 检查任务完成条件；auditor 检查引用、矛盾、越权和敏感推断。
10. 通过则返回，不通过则带结构化问题有限次回到检索或生成节点。

## 难点

| 难点 | 级别 | 做法 | 残余风险 |
|---|---|---|---|
| 结构保真 | P1 | chunk_by_title、表格原子化 | 扫描表格和跨页表仍难，需要 VLM/OCR 校验 |
| 查询-原文词差 | P1 | 摘要/关键词/假设问题 | 生成内容不能作为最终事实证据 |
| 精排 | P1 | top-20 -> CrossEncoder top-5 | 模型应做中文招聘数据微调/评估 |
| 独立审计 | P0/P1 | auditor/red team | 审计模型也会错，关键决策仍需人工 |
| 成本 | P2 | 索引时富化 | 对稳定文档离线生成并按哈希缓存 |

## SmileBoss 借鉴

对简历每段经历生成“可能被招聘方怎样查询”的 hypothetical questions，可显著增强隐含技能召回；但推荐理由只能引用原始经历，不能引用假设问题。引入 `PolicyAuditor` 节点检查敏感属性、证据缺失、绝对化语言和越权访问。CrossEncoder 可先以可插拔接口实现，初期用现有 LLM listwise 重排，积累数据后换专用重排模型。

## 评分

架构学习 5/5；生产成熟度 2/5；招聘相关性 4/5；接入成本 3/5；适合作为“检索增强 + 独立审计”参考。

