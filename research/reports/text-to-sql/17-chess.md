# 17. CHESS：IR、Schema Selector、Candidate Generator、Unit Tester 四 Agent

## 项目卡片

- GitHub：[ShayanTalaei/CHESS](https://github.com/ShayanTalaei/CHESS)
- 本地源码：`research/source-code/agent-text2sql/17-chess`
- 锁定版本：`3d6e835f858d`
- 定位：Contextual Harnessing for Efficient SQL Synthesis，面向大数据库 Schema 的多 Agent Text-to-SQL。
- 源码入口：`src/workflow/agents/`、`src/workflow/team_builder.py`、`src/runner/database_manager.py`、`src/database_utils/execution.py`。
- 一句话判断：CHESS 是 10 个 Text-to-SQL 项目中“Agent 分工最有验证价值”的一个：四个 Agent 分别对应信息检索、Schema 裁剪、候选生成/修订和自然语言单元测试。
- 重要性：P1，安全改造为 P0。

## STAR

### S — Situation

工业数据库目录很大，问题中的实体值未必出现在列名中，LLM 无法把全 Schema 塞进上下文；即便 SQL 能执行，也可能语义错误。项目将挑战归纳为：大 catalog/值、Schema 推理、功能有效性和自然语言歧义。

### T — Task

用四个专业 Agent 逐步缩小问题：IR 找相关上下文和值，SS 裁剪表列，CG 生成并迭代修订候选，UT 通过自然语言单元测试和执行结果选择正确查询。

### A — Action

预处理阶段为每个数据库建立 minhash、LSH 和 vector databases。Information Retriever 的工具包括 `extract_keywords`、`retrieve_entity`、`retrieve_context`，负责实体值和相关知识。Schema Selector 用 `select_tables`、`select_columns`、`filter_column` 将全 Schema 收缩为 tentative schema。Candidate Generator 的 `generate_candidate` 生成一个或多个 SQL，`revise` 根据 Schema/执行信息迭代。Unit Tester 的 `generate_unit_test` 从自然语言问题产生测试条件，`evaluate` 将候选 SQL及其执行结果格式化比较，选择满足测试的候选。`DatabaseManager` 管理 Schema、示例值和执行，`execution.py` 提供超时。

### R — Result

官方报告 Schema Selector 约提升 2% 准确率并减少约 5 倍 token；高预算设置在 BIRD 上达到 71.10%，相较领先专有路线减少约 83% LLM calls。指标来自项目/论文，不代表 SmileBoss 数据效果。主要风险是研究代码面向 benchmark/SQLite，生产权限、MySQL 方言、并发和审计需要重做；LLM 生成的自然语言单元测试也不是绝对真值。

## 完整执行流

1. **预处理**：读取数据库 catalog 和值，构建 MinHash/LSH/vector 索引。
2. **IR-关键词**：从问题和 evidence 抽关键词、实体、时间和业务短语。
3. **IR-值链接**：用 LSH/向量找到数据库中相似值，帮助判断条件落在哪列。
4. **IR-上下文**：检索相关示例和说明。
5. **SS-表选择**：从全库选择候选表，保留连接所需桥表。
6. **SS-列选择**：选择过滤、聚合、连接和输出列；filter_column 清理噪声。
7. **CG-生成**：基于问题、证据、裁剪 Schema 和示例生成多候选。
8. **CG-修订**：语法/执行/逻辑问题进入 revise。
9. **UT-生成测试**：把问题转为自然语言断言，例如时间范围、分组粒度、返回列。
10. **UT-执行评估**：运行候选，比较执行结果与测试条件，选择最终 SQL。
11. 保存每个 Agent 的中间结果、统计和最终预测。

## 四 Agent 的职责边界

| Agent | 只负责 | 不应负责 |
|---|---|---|
| IR | 找值、术语、上下文 | 直接决定最终 SQL |
| SS | 裁剪表列并保留 join path | 用用户权限外的 Schema |
| CG | 生成/修订候选 | 自己宣布结果正确 |
| UT | 生成可验证条件、比较执行结果 | 绕过安全规则执行任意 SQL |

## 难点

| 难点 | 级别 | CHESS 做法 | SmileBoss 改造 |
|---|---|---|---|
| 大 Schema | P1 | SS 剪枝 | 先语义层/权限裁剪，再二次表列选择 |
| 实体值链接 | P1/P0 | MinHash/LSH/vector | 只索引允许暴露的枚举/脱敏值，不嵌入 PII |
| 语义正确性 | P1 | NL unit tests | 增加确定性断言、结果不变量和 golden tests |
| 执行超时 | P0/P1 | execution timeout | 只读副本、EXPLAIN 成本、行数和并发限制 |
| Benchmark 到生产 | P1 | 研究流水线 | MySQL 方言、多租户、错误脱敏、审计重构 |

## SmileBoss 借鉴

核心方案应采用 CHESS 四阶段，但 Agent 数量可表现为四个工作流节点而非四段自由对话。IR 检索招聘指标定义和允许枚举；SS 在用户可见语义模型内裁剪；CG 用现有多模型产生候选；UT 把“近 30 天、按天、去除测试账号、仅当前租户”等转成断言并验证 SQL AST/结果。最终仍由安全执行层决定能否运行。

## 评分

架构学习 5/5；研究实现成熟度 4/5；招聘相关性 5/5；接入成本 2/5；SmileBoss Agentic Text-to-SQL 的核心算法参考。

