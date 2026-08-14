# 20. IBM Text2SQL Evaluation Toolkit：执行准确率、Agentic 评估与错误分析

## 项目卡片

- GitHub：[IBM/text2sql-eval-toolkit](https://github.com/IBM/text2sql-eval-toolkit)
- 本地源码：`research/source-code/agent-text2sql/20-text2sql-eval-toolkit`
- 锁定版本：`60dd4515236a`
- 定位：Text-to-SQL benchmark、推理适配、执行评估、指标和错误分析工具箱。
- 一句话判断：它不是 SQL 生成器，却是能否上线 Text-to-SQL 的关键项目；没有执行级和安全级评估，任何模型演示都不能证明可用。
- 重要性：P0/P1。

## STAR

### S — Situation

字符串与标准 SQL 不同不代表错误：列顺序、别名、等价 join 或条件写法可能不同；反过来，SQL 看起来很像却可能因重复行、NULL、时间边界而得到错误结果。Agentic 流程还要评价工具调用次数、修复和成本。

### T — Task

统一数据集/数据库适配、标准与 Agentic 推理输出，安全执行预测和参考 SQL，比较结果集并提供 SQL/LLM judge 等辅助指标与错误分析。

### A — Action

Toolkit 加载 benchmark 样本和数据库，调用配置的 inference pipeline 获取 SQL/轨迹；在受控数据库执行预测与 gold，规范化排序、NULL、浮点等结果差异并比较。可计算语法/结构/执行指标，Agentic 设置记录工具与迭代；错误按 Schema link、join、filter、aggregation、value、syntax 等维度分析并形成报告/看板。

### R — Result

执行准确率比文本匹配更接近真实正确性，分类错误能指导优化 IR、SS、CG 或 UT 的具体节点。困难是 gold SQL/结果也可能有问题，非确定查询、时间/随机函数和大结果集难比较，LLM judge 不能替代确定性执行。

## 完整执行流程（评估流）

1. 建立脱敏的招聘 NL2SQL 金标集，冻结数据库快照。
2. 每条样本包含问题、用户角色、语义范围、参考 SQL、期望结果/不变量和难度标签。
3. 调用 pipeline，保存候选 SQL、检索上下文、Agent 轨迹、token 和延迟。
4. 在隔离数据库执行预测和 gold。
5. 规范化并比较结果；同时检查 SQL 安全违规。
6. 统计 execution accuracy、valid SQL、timeout、unsafe rate、cost、latency。
7. 按错误类别和查询复杂度切片，定位需要优化的节点。
8. CI/发布门禁比较新旧版本，显著回归则阻止上线。

## 必须建立的 SmileBoss 指标

| 指标 | 级别 | 含义 |
|---|---|---|
| Safe execution rate | P0 | 未越权、只读、未超成本的比例，目标应接近 100% |
| Execution accuracy | P1 | 结果与金标等价的比例 |
| Schema selection recall | P1 | 正确表列是否进入生成上下文 |
| Clarification quality | P1 | 歧义问题是否主动澄清而非猜测 |
| Empty-result diagnosis | P1 | 真为空与错误过滤是否区分 |
| P95 latency/token cost | P2 | 生产体验与预算 |
| Group/privacy violations | P0 | 小样本、敏感列、跨租户输出次数 |

## 难点

时间边界必须固定时区和“当前时间”；结果集比较需处理顺序、重复、浮点误差和 NULL；大查询使用聚合 hash/抽样但需防碰撞；错误消息要分类而不能把完整数据泄露给模型。招聘数据金标由数据工程与业务共同审核，不能只从历史看板 SQL 自动生成。

## SmileBoss 借鉴

先做评估集再写 Agent。至少覆盖：单表过滤、跨表连接、漏斗、时间窗口、去重、状态码、软删除、租户、权限拒绝、歧义澄清和恶意指令。每次更换模型、prompt、embedding、语义层或 Schema 都自动回归。评估数据库使用合成/脱敏快照，绝不在生产库跑生成候选。

## 评分

架构学习 5/5；生产成熟度 4/5；招聘相关性 5/5；接入成本 4/5；上线门禁的首要参考。
