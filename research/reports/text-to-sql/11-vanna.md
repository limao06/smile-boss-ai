# 11. Vanna：DDL、业务文档和历史 SQL 的示例 RAG

## 项目卡片

- GitHub：[vanna-ai/vanna](https://github.com/vanna-ai/vanna)
- 本地源码：`research/source-code/agent-text2sql/11-vanna`
- 锁定版本：`365d0617c1a4`
- 定位：以 DDL、documentation、question-SQL pairs 训练/检索上下文，再生成和执行 SQL；v2 强调 user-aware tools、权限和生命周期 hooks。
- 状态说明：仓库在研究日期前后已归档，适合学习模式，不建议无评估地作为长期基座。
- 一句话判断：Vanna 证明 Text-to-SQL 的 RAG 不是检索业务数据行，而是检索最相关的 Schema、规则和“已验证问法—SQL”示例。
- 重要性：P1，执行安全为 P0。

## STAR

### S — Situation

模型只看到完整 DDL 时上下文过大，且不了解公司如何定义“活跃账号”“有效投递”“面试通过率”。相同自然语言在不同企业对应不同 SQL，通用模型很难猜对。

### T — Task

将 DDL、业务文档和已验证问答 SQL 作为知识资产建立向量索引；对新问题只取相关上下文生成 SQL，可选执行并返回表格、图表和摘要，同时让用户和工具权限进入运行上下文。

### A — Action

经典流程通过 `train` 加入 DDL、documentation 和 question-SQL examples。新问题到来时分别检索相关 DDL、文档和类似 SQL，拼成提示词后生成 SQL；可验证是否 SQL、执行查询、生成 Plotly 图或自然语言摘要。v2 将能力拆为 Agent tools，并增加 user-aware 权限、生命周期 hooks、SSE 和行级控制思路。

### R — Result

少量高质量业务示例能显著稳定 SQL 风格和指标定义，且新知识无需微调模型。风险是错误历史 SQL会被重复放大、向量相似不保证 Schema 连接正确、执行层若直连生产可造成泄露或资源事故；归档状态影响长期维护。

## 完整执行流

1. 管理员同步 DDL，添加表/列描述和业务术语。
2. 将经过审核的 `question -> SQL -> dialect -> result shape` 作为 golden examples。
3. 对这些知识生成 embedding 并写向量库。
4. 用户问题先绑定身份、租户和允许的数据域。
5. 检索相关 DDL、文档和相似问答 SQL。
6. 组装受 token 限制的 prompt，生成候选 SQL。
7. SQL parser/AST 检查只读、允许表列、LIMIT 和复杂度。
8. 在只读连接/副本以超时和行数限制执行。
9. 若数据库报错，提供最小错误信息进行有限次修复。
10. 返回 SQL、结果、解释、使用的业务定义和审计记录。

## 难点

| 难点 | 级别 | Vanna 模式 | SmileBoss 控制 |
|---|---|---|---|
| 业务定义 | P1 | documentation RAG | 指标定义版本化，显示口径 |
| 示例质量 | P1 | Q-SQL pairs | 仅审核通过才进 golden 库，负反馈可下线 |
| 权限 | P0 | v2 user-aware | 先裁剪 Schema，再生成，不是生成后过滤 |
| SQL 执行 | P0 | 可插拔执行函数 | 只读账号、副本、AST、超时、行数/成本限制 |
| 项目归档 | P1 | 维护停止 | 复用思想，不绑定实现 |

## SmileBoss 借鉴

在 Qdrant 建 `sql_knowledge` collection，存三类对象：Schema 单元、指标/业务术语、审核通过的问答 SQL。Embedding 沿用 Qwen 1024 维。MySQL 保留 golden SQL 的版本、审批人和权限域。不要把真实候选人数据行嵌入这个知识库。

## 评分

架构学习 5/5；生产成熟度 2/5（归档因素）；招聘相关性 4/5；接入成本 4/5；示例 RAG 的首要入门参考。

