# 09. Open Deep Research：监督者—研究员并发与有界工具循环

## 项目卡片

- GitHub：[langchain-ai/open_deep_research](https://github.com/langchain-ai/open_deep_research)
- 本地源码：`research/source-code/agent-text2sql/09-open-deep-research`
- 锁定版本：`1b7d2e80db9f`
- 定位：LangGraph 深度研究 Agent，支持搜索和 MCP，可配置 supervisor/researcher。
- 一句话判断：它把复杂报告拆成“澄清范围—研究简报—监督者并发委派—研究员工具循环—笔记汇总—最终写作”，是生产化研究协作的优秀参考。
- 重要性：P1/P2。

## STAR

### S — Situation

用户请求常含模糊范围；立即并发搜索会浪费成本并产生互相重复的研究。监督者若既研究又写作，也容易丢失全局覆盖度。

### T — Task

先澄清并生成 research brief；监督者分解主题、并行派发研究员；研究员在有界循环内调用搜索/MCP 工具并产出 notes；最终 writer 只基于 notes 写报告。

### A — Action

当前主流程在 `deep_researcher.py` 等模块中组织 supervisor-researcher。Scope 节点判断是否需要澄清，随后生成研究简报。Supervisor 维护主题覆盖并通过工具/消息委派多个 researcher。每个 researcher 可执行若干搜索或 MCP 调用，压缩为结构化笔记；并发数、工具调用数和迭代数可配置。Supervisor 收齐后交给 final report 节点。仓库还保留较早的顺序计划/HITL 和多 Agent 变体，便于比较。

### R — Result

并发提高覆盖和速度，研究简报减少跑偏，笔记隔离写作者和原始噪声。风险是 supervisor 可能拆出重叠主题，MCP 工具权限扩大攻击面，研究员压缩可能丢证据；最终 writer 仍需引用验证。

## 完整执行流

1. 用户问题 -> scope 判断；必要时只问一个关键澄清。
2. 形成 ResearchBrief：目标、范围、交付物、来源限制。
3. Supervisor 拆成互补 topics 并设预算。
4. 并发启动 researcher；每个只获得必要工具和子任务。
5. Researcher 执行搜索/读取/反思的有限循环。
6. 输出 ResearchNotes：claims、sources、conflicts、unknowns。
7. Supervisor 去重、检查覆盖，必要时追加一次定向研究。
8. Writer 根据 notes 和 brief 生成报告。
9. Citation validator 检查每个事实是否被来源直接支持。

## 难点

| 难点 | 级别 | 做法 | 控制 |
|---|---|---|---|
| 任务分解 | P1 | supervisor delegation | 子任务互斥/穷尽检查，按来源去重 |
| 并发预算 | P2 | configurable limits | 设每研究员和全局预算 |
| 工具安全 | P0 | MCP/search | 最小权限、来源白名单、内容不可信标记 |
| 笔记压缩 | P1 | structured notes | 保留原始 source/span ID |
| 最终引用 | P1 | writer | 写后逐句 claim-citation 验证 |

## SmileBoss 借鉴

用于生成岗位调研、招聘周报和候选综合报告。Supervisor 不读取超出用户权限的库；Researcher 的查询结果先经过 PII 和策略过滤。实时 AI 面试不要运行大规模深度研究，避免延迟和不可控外部访问。

## 评分

架构学习 5/5；生产成熟度 4/5；招聘相关性 4/5；接入成本 3/5；并发研究与长报告的首要参考。

