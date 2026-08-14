# 19. SQLCoder：专用 Text-to-SQL 模型基线

## 项目卡片

- GitHub：[defog-ai/sqlcoder](https://github.com/defog-ai/sqlcoder)
- 本地源码：`research/source-code/agent-text2sql/19-sqlcoder`
- 锁定版本：`de7249834e4f`
- 定位：专用开源 Text-to-SQL 模型、提示模板和本地推理示例。
- 一句话判断：SQLCoder 代表“模型能力基线”：把充分的 Schema metadata 注入固定 prompt，由专用模型直接生成 SQL；它能作为生成器，但不包含完整的权限、检索、执行修复和语义治理。
- 重要性：P1。

## STAR

### S — Situation

通用聊天模型不一定熟悉复杂连接、窗口函数和 SQL 形式。专门以 Text-to-SQL 数据训练的模型可能在较小参数量下提供更稳定的 SQL 语法，并能本地部署。

### T — Task

用固定模板描述问题、表、列、关系和必要提示，通过 Transformers/llama.cpp 等本地推理生成 SQL，为系统提供一个可独立评估的专用生成器。

### A — Action

调用端将 Schema metadata 和自然语言问题填入 prompt，模型自回归生成 SQL，再提取 SQL 代码。项目提供不同推理方式和硬件说明；模型训练基于整理的 SQL 问答数据。核心并不负责向量检索、Schema 选择、权限或安全执行。

### R — Result

可形成低成本、本地化生成基线，也可作为 XiYan 式多候选中的一个 generator。限制是上下文过大时性能下降，业务术语和 MySQL 特性需额外适配；“生成语法正确”并不等于结果正确或允许执行。

## 完整执行流

1. 外部系统先按权限选择相关 Schema。
2. 构建 SQLCoder 要求的 prompt，包含 DDL/关系/问题。
3. 本地模型生成 tokens，截取 SQL。
4. 外部 parser 验证方言和只读性。
5. 外部安全执行层运行和评估；必要时由 refiner 修复。

## 难点与 SmileBoss 借鉴

| 难点 | 级别 | 建议 |
|---|---|---|
| 上下文限制 | P1 | 先做语义层和 CHESS 式 Schema 裁剪 |
| 领域术语 | P1 | 示例 RAG 或领域微调，不堆长 prompt |
| 硬件 | P2 | 用真实 QPS/延迟评估量化版本 |
| 方言 | P1 | MySQL 专项测试，禁止默认按 PostgreSQL 语义 |
| 安全缺失 | P0 | 模型外部强制 AST/ACL/执行沙箱 |

先用现有模型建立强基线，再在相同评估集比较 SQLCoder 的执行准确率、延迟、成本和隐私收益。只有总体收益明确时才承担本地模型运维。

## 评分

架构学习 3/5；生成器成熟度 4/5；招聘相关性 3/5；接入成本 3/5；适合作为专用生成模型基线。

