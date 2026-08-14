# 03. CrewAI：角色化 Crew 与事件驱动 Flow

## 项目卡片

- GitHub：[crewAIInc/crewAI](https://github.com/crewAIInc/crewAI)
- 本地源码：`research/source-code/agent-text2sql/03-crewai`
- 锁定版本：`754d7323beb2`
- 定位：Agent、Task、Crew、Process 与 Flow 编排框架。
- 一句话判断：CrewAI 很适合快速表达角色、任务和交付物；生产设计应以 Flow 控制状态，以 Crew 处理局部开放任务，而不是让角色一直互聊。
- 重要性：P1/P2。

## STAR

### S — Situation

用户容易理解“研究员、写作者、审稿人”的分工，但如果只有角色提示词，任务依赖、输出格式、异步关系和失败条件并不清晰。

### T — Task

用 Agent 描述能力和工具，用 Task 规定 expected_output/context，用 Crew 的 sequential/hierarchical process 组织协作；用 Flow 的 state、start/listen/router 控制更长生命周期。

### A — Action

Agent 配置 role、goal、backstory、tools、memory 和 delegation。Task 指定描述、预期输出、负责 Agent、上游 context、异步和 guardrail。Crew kickoff 后按 sequential 或 manager 驱动的 hierarchical process 执行。Flow 维护结构化 state，通过 `@start` 触发，`@listen` 接收事件，`@router` 条件分流，可在某节点调用一个 Crew 并把产物写回状态。

### R — Result

业务角色映射直观，适合原型和报告写作；Flow/Crew 组合比单纯群聊更可控。风险是 backstory 容易制造“角色感”却不增加可靠性，delegation 可能递归扩散，memory 和外部工具权限需要额外治理。

## 完整执行流

1. 定义 Flow state 和触发事件。
2. 建 Agent：能力、工具、不可做事项。
3. 建 Task：输入、expected_output Schema、context、guardrail。
4. Crew 按 sequential/hierarchical 执行局部任务。
5. 产物经 guardrail 验证后返回 Flow。
6. Router 根据质量/风险/缺口进入修订、人工审批或完成分支。
7. 记录任务、Agent、工具调用和最终产物。

## Agent 写作协作

推荐“研究员 -> 提纲师 -> 分节作者并行 -> 事实核查 -> 风格编辑 -> 总审稿”。各 Agent 交换的是 `ResearchNote`、`Outline`、`SectionDraft`、`ReviewIssue`，不是整段聊天。编辑只能改变表达，不能无证据新增事实；事实核查者输出问题清单而不是直接偷偷改稿。

## 难点

| 难点 | 级别 | CrewAI 能力 | 建议 |
|---|---|---|---|
| 交付物明确 | P1 | expected_output/context | 强制 JSON Schema 和证据 IDs |
| 层级管理 | P1 | hierarchical process | 经理只分配/验收，不代替专业节点 |
| 委派失控 | P2 | delegation | 最大深度、任务数和预算 |
| 长流程状态 | P1 | Flow state | 业务真相放 MySQL，不只在内存 memory |
| 内容幻觉 | P1 | guardrails | 独立 evidence checker + 引用验证 |

## SmileBoss 借鉴

候选报告可采用 Crew 局部生成：证据研究、技能摘要、面试摘要和风险审查；外层仍由确定性 Flow 控制。不要把“HR Agent”“面试官 Agent”的人格描述当权限模型，工具权限必须由服务端赋予。

## 评分

架构学习 4/5；生产成熟度 3/5；招聘相关性 4/5；接入成本 3/5；适合快速实现局部协作。

