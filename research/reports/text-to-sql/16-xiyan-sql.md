# 16. XiYan-SQL：M-Schema、多生成器、修复与选择

## 项目卡片

- GitHub：[XGenerationLab/XiYan-SQL](https://github.com/XGenerationLab/XiYan-SQL)
- 本地源码：`research/source-code/agent-text2sql/16-xiyan-sql`
- 锁定版本：`603dedac706d`
- 定位：Text-to-SQL 系列项目/论文总入口，核心能力分布在相关子项目、模型和 MCP 服务中。
- 源码注意：本仓库当前仅少量入口文件，更多是架构、论文和相关实现索引；报告明确区分官方设计与此仓库实际代码。
- 一句话判断：它的关键思路是不要押注单一生成器：用紧凑 M-Schema 表示上下文，多种生成器产出候选，refiner 修复，selector 选择。
- 重要性：P1。

## STAR

### S — Situation

不同 Text-to-SQL 路线各有优势：强通用模型善于推理，微调模型更熟悉 SQL 形式，ICL 示例对特定模式有效。单次生成的随机错误难靠同一个模型自证；大 Schema 还会挤占上下文。

### T — Task

用 M-Schema 紧凑表达表、列、关系和示例值；组合 ICL 和微调生成器产生多候选；由 refiner 修复语法/逻辑，由 selector 结合问题、Schema 和执行信号选出最终 SQL，并适配多方言/日期实体。

### A — Action

官方体系先构建 M-Schema，必要时做日期/实体解析；不同 generator 并行或分阶段生成 SQL。Refiner 使用 Schema、错误或执行反馈修订候选；Selector 对多个候选进行比较选择。XiYan MCP 等组件让数据库工具可被 Agent 调用，多方言适配扩展到真实数据源。

### R — Result

多样化候选能降低单模型偶然失败，M-Schema 控制 token，选择器比“第一个能执行的 SQL”更可靠。代价是调用成本增加、候选可能高度同质，selector 也会选错；本仓库并非所有核心实现的一站式源码。

## 完整执行流（官方体系）

1. 发现 Schema，转换为 M-Schema。
2. 解析问题中的时间、实体和值，检索相关 Schema/示例。
3. ICL generator 和一个或多个 fine-tuned generator 产生候选 SQL。
4. 对候选做 parser/方言/权限检查。
5. Refiner 根据错误、Schema 和约束修订。
6. 在安全环境执行候选或 EXPLAIN，收集信号。
7. Selector 比较语义、执行结果和复杂度，选最终 SQL。
8. 返回 SQL、选择理由和审计。

## 难点

| 难点 | 级别 | 设计 | SmileBoss 控制 |
|---|---|---|---|
| Schema 压缩 | P1 | M-Schema | 保留关系、说明、枚举和权限，不只列名 |
| 候选多样性 | P1 | 多生成器 | 使用不同提示/模型，计算 AST 差异 |
| 候选选择 | P1 | selector | 执行等价性优先，LLM 判断为辅助 |
| 工具协议 | P0 | MCP | 数据库工具只读、参数化、最小权限 |
| 源码分散 | P2 | 子项目生态 | 采用前逐个审查许可证与版本 |

## SmileBoss 借鉴

高风险/复杂查询可用 Claude 与 Qwen/DeepSeek 生成 2–3 个候选，先 AST 和安全过滤，再在只读库执行小结果/EXPLAIN，按结果一致性和语义 rubric 选择。简单查询只用单模型，避免无谓成本。模型 fallback 不等于候选 ensemble：fallback 仅在失败时替代，ensemble 是主动多样化并比较。

## 评分

架构学习 5/5；本仓库实现完整度 2/5；招聘相关性 4/5；接入成本 2/5；多候选路线的重要研究参考。

