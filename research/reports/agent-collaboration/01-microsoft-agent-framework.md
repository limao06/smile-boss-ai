# 01. Microsoft Agent Framework：Agent 与确定性 Workflow 的统一运行时

## 项目卡片

- GitHub：[microsoft/agent-framework](https://github.com/microsoft/agent-framework)
- 本地源码：`research/source-code/agent-text2sql/01-microsoft-agent-framework`
- 锁定版本：`ae7fa3389c8f`
- 定位：Python/.NET Agent 与工作流框架，承接 AutoGen 与 Semantic Kernel 的演进方向。
- 一句话判断：它最适合学习“什么时候让 Agent 决策，什么时候用确定性边控制”，以及如何把 checkpoint、handoff、并行和 HITL 变成运行时能力。
- 重要性：P1。

## STAR

### S — Situation

多 Agent 系统如果只靠彼此聊天，会出现责任不清、消息无限增长、重复调用工具和失败后无法恢复。招聘面试尤其需要可审计的顺序、审批点和确定终止条件。

### T — Task

统一 agent/thread/tool/context provider 抽象，并用 workflow 的 executor、edge 和事件定义顺序、并发、条件、group chat、handoff 与人工介入；支持流式输出、检查点和托管运行。

### A — Action

开发者先创建模型 client、tools 和具有指令的 Agent；需要自由对话时通过 thread 保存会话。跨角色任务则定义 workflow：executor 接收事件和状态、执行 Agent/函数并向 edge 发送结构化产物。边可以顺序、并发、条件、handoff 或 group chat。运行时记录事件，长流程使用 checkpoint；到高风险节点可 interrupt 等待人工，再从同一状态继续。MCP/A2A 等协议用于外部工具或 Agent 互通。

### R — Result

自由推理与确定性控制可以共存，失败恢复和部署边界更清晰。风险是框架仍在快速演进，过度抽象会掩盖领域状态；跨进程 Agent 还会带来协议、幂等和安全复杂度。

## 完整执行流

1. 构造模型 client、工具白名单和 Agent instructions。
2. 定义强类型 workflow state 和每个 executor 的输入/输出事件。
3. 连接 sequential/concurrent/conditional/handoff edges。
4. 用户事件进入 workflow，运行时分发给首个 executor。
5. executor 调用 Agent/工具，更新允许的状态字段并发布下一事件。
6. 并发分支独立工作，在 join 点合并结构化结果。
7. 风险节点触发 HITL；批准/修改/拒绝作为新事件写回。
8. 每一步 checkpoint；失败从最近检查点重放，并用 idempotency key 防重复副作用。
9. 达到终止事件后输出最终产物和完整 trace。

## Agent 间协作契约

不要传“请接着处理”这种模糊文本，应传：`task_id`、`goal`、`input_evidence_ids`、`artifact_schema`、`budget`、`deadline`、`policy_scope`、`status`。接收方输出 `artifact`、`evidence`、`uncertainties`、`next_action`，监督者据此路由。

## 难点

| 难点 | 级别 | 框架能力 | SmileBoss 要求 |
|---|---|---|---|
| 自由与确定性边界 | P1 | Agent + workflow | 选题可智能，权限/轮次/评分发布必须确定性 |
| 恢复 | P1 | checkpoint | 外部工具调用加幂等键 |
| 人工审批 | P0/P1 | interrupt/HITL | 淘汰、敏感结论、正式面试报告需审批策略 |
| 跨 Agent 上下文 | P1 | thread/context | 只传任务需要的最小证据 |
| 协议安全 | P0 | MCP/A2A | 工具能力、参数和网络目标白名单 |

## SmileBoss 借鉴

Java 主服务可复刻其模式，不必立即引入 Python/.NET runtime。AI 面试状态机用确定性 workflow；只有问题生成、证据判断和报告撰写使用模型。任何 Agent 无权直接改变候选状态；只能产生“建议动作”，由业务服务校验和人工策略决定。

## 评分

架构学习 5/5；生产成熟度 4/5；招聘相关性 4/5；接入成本 2/5；作为多 Agent 运行时设计的首要参考。

