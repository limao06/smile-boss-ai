# 02. LangGraph：有状态图、Reducer、检查点与 Human-in-the-Loop

## 项目卡片

- GitHub：[langchain-ai/langgraph](https://github.com/langchain-ai/langgraph)
- 本地源码：`research/source-code/agent-text2sql/02-langgraph`
- 锁定版本：`644815f9e5bc`
- 定位：受 Pregel 启发的有状态 Agent 工作流图。
- 一句话判断：LangGraph 最强的不是“画图”，而是明确状态 Schema、字段合并 reducer、superstep、线程检查点、interrupt 和 time travel。
- 重要性：P1。

## STAR

### S — Situation

复杂 Agent 有条件分支、循环、并行和长时间等待。普通函数链难恢复，纯聊天群难保证谁能改哪个状态；并行节点同时写同一字段还可能互相覆盖。

### T — Task

以 typed state 为中心定义 node/edge，使用 reducer 规定并发更新怎样合并；按 superstep 执行就绪节点，持久化 thread checkpoint，并支持中断、恢复、回放和子图。

### A — Action

开发者定义 StateGraph 和状态字段；节点读取当前状态并返回增量更新，reducer 决定追加、覆盖或自定义合并。普通边和 conditional edges 控制路由，Send 可动态 fan-out。compile 时接入 checkpointer。每次 invoke 带 thread_id；interrupt 将待审批状态持久化，恢复时通过 Command 提交人工决定。子图封装局部循环，stream 输出节点级事件。

### R — Result

可重放、有边界的状态机非常适合面试和深度推荐。主要风险是 reducer 设计错误造成隐性数据丢失，checkpoint 里可能保存 PII，循环若无终止守卫仍会失控；框架语义需要团队学习。

## 完整执行流

1. 定义 `InterviewState` 及 reducer。
2. 添加 plan/ask/evaluate/followup/audit/finalize 节点。
3. 条件边根据证据充分性、剩余时间和风险路由。
4. compile + checkpointer；以 interview_session_id 作为 thread_id。
5. invoke 后按 superstep 执行可运行节点，并持久化状态版本。
6. 并行评分器通过 reducer 合并维度分和证据。
7. 遇到敏感/低置信度结论 interrupt，人工处理后 resume。
8. 可回到历史 checkpoint 修正输入并 fork，而不破坏原审计链。

## 难点

| 难点 | 级别 | 做法 | 控制点 |
|---|---|---|---|
| 状态合并 | P1 | reducer | 证据按 ID 去重，评分不可最后写入覆盖 |
| 循环终止 | P1 | conditional edge | `max_turns/max_followups/deadline/budget` 四重限制 |
| PII 持久化 | P0 | checkpointer | 加密、最小字段、保留期和租户隔离 |
| 重放副作用 | P0/P1 | checkpoint/time travel | 发消息/写状态等外部动作必须幂等 |
| 子图边界 | P1 | subgraph | 只暴露输入/输出 Schema |

## SmileBoss 借鉴

若不引入 LangGraph，也要实现相同语义：不可变 run/event、版本化 state、节点增量更新、条件边、检查点和人工恢复。Redis 只用于实时状态，MySQL 保存审计真相；敏感转写不应无限期进入 checkpoint。

## 评分

架构学习 5/5；生产成熟度 5/5；招聘相关性 4/5；接入成本 3/5；AI 面试状态机最强参考之一。

