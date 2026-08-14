# 12. WrenAI：MDL 语义层与受治理的 Text-to-SQL

## 项目卡片

- GitHub：[Canner/WrenAI](https://github.com/Canner/WrenAI)
- 本地源码：`research/source-code/agent-text2sql/12-wrenai`
- 锁定版本：`7f7370e4e9b0`
- 定位：GenBI/语义层平台，使用 Modeling Definition Language 描述模型、关系、计算、指标和访问逻辑。
- 一句话判断：这是 SmileBoss 做招聘数据自然语言问答最重要的架构参考，因为它把“LLM 猜数据库”改成“LLM 面向受治理的业务语义模型，再编译到物理 SQL”。
- 重要性：P0/P1。

## STAR

### S — Situation

真实数据库表名、软删除、状态码和 join 逻辑并不等于业务语言。用户问“近 30 天活跃招聘方”，模型需要知道活跃定义、时区、租户、测试账号排除和事件去重。把这些规则写在 prompt 中难版本化，也无法被 BI 和 API 共同复用。

### T — Task

在物理数据库之上建立语义模型：实体、字段、关系、计算字段、指标和治理规则。LLM 面向语义上下文生成语义查询/SQL，由引擎编译为具体数据源 SQL并执行。

### A — Action

管理员通过 MDL 定义 models、relationships、calculated fields、metrics 和可见范围；知识层补充业务规则与 SQL 示例。用户问题先做 discovery/context selection，生成与语义 manifest 对齐的查询。语义引擎（项目中含 Rust/DataFusion 等组件）解析和编译为底层数据库方言，执行后返回结果和解释。其产品理念常概括为 Generate、Deploy、Know：生成模型、部署受治理语义层、用知识提高问答。

### R — Result

指标定义集中、一致、可测试，LLM 不必接触全量物理 Schema，权限和连接路径也更可控。代价是前期建模投入大，语义层与数据库变更要同步；复杂 SQL 仍需验证，错误的统一口径会影响所有消费者。

## 完整执行流

1. 从 MySQL 发现物理表、列、主外键和统计信息。
2. 数据工程/业务共同定义 MDL：Candidate、Job、Application、Interview、ActivityEvent。
3. 定义关系、派生字段、指标、时区、软删除和租户规则。
4. 发布语义模型版本并运行编译/回归测试。
5. 用户问题经身份和数据域识别。
6. 检索相关语义对象、业务规则和 golden examples。
7. LLM 生成基于语义对象的查询计划。
8. Compiler 解析关系并翻译为 MySQL SQL。
9. 安全验证、只读执行、结果质量检查。
10. 返回结果、SQL、指标定义、数据新鲜度和语义模型版本。

## 难点

| 难点 | 级别 | WrenAI 做法 | SmileBoss 要求 |
|---|---|---|---|
| 业务/物理解耦 | P1 | MDL | 先建 10–20 个核心指标，不追求全库 |
| 统一口径 | P1 | calculations/metrics | 指标 owner、版本、测试和变更审批 |
| Join 路径 | P1 | relationships | 显式基数和允许路径，防止重复计数 |
| 权限治理 | P0 | governed context | tenant/role/row policies 在编译前应用 |
| 模型同步 | P1 | deploy/version | DB migration CI 检查语义模型 |

## SmileBoss 借鉴

建立轻量 `Recruitment Semantic Layer`，至少定义：活跃招聘方、活跃候选人、有效投递、面试完成、面试通过、推荐曝光/点击/投递、职位转化、招聘周期。Agent 只能查询这个层暴露的对象。它比直接给 LLM `information_schema` 安全得多，也能与管理看板共用口径。

## 评分

架构学习 5/5；生产成熟度 5/5；招聘相关性 5/5；接入成本 2/5；Text-to-SQL 设计的第一优先参考。

