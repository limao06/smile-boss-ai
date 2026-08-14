# 04. AutoGen：消息驱动的 Core、AgentChat 与扩展层

## 项目卡片

- GitHub：[microsoft/autogen](https://github.com/microsoft/autogen)
- 本地源码：`research/source-code/agent-text2sql/04-autogen`
- 锁定版本：`027ecf0a379b`
- 定位：事件/消息驱动多 Agent 框架；当前官方方向已转向 Microsoft Agent Framework，AutoGen 以维护为主。
- 一句话判断：AutoGen 对理解 RoundRobin、Selector、Swarm、Magentic-One、termination condition 和分层 runtime 很有价值，但新项目应评估 Agent Framework 而非锁死 AutoGen。
- 重要性：P1，采用状态为“研究/迁移参考”。

## STAR

### S — Situation

多 Agent 可能需要不同会话模式：轮流发言、由模型选下一位、Agent 主动 handoff、计划者动态分配，或通过 topic pub/sub 解耦。单一 orchestrator 难覆盖所有模式。

### T — Task

Core 提供消息、topic 和本地/分布式 runtime；AgentChat 提供可用团队模式和终止条件；Extensions 集成模型、工具和代码执行器。

### A — Action

Core 中 Agent 订阅 topic，runtime 路由消息并维护生命周期。AgentChat 封装 AssistantAgent、UserProxy 和 Team；RoundRobin 固定轮换，Selector 由模型选择下一 Agent，Swarm 通过 handoff 转移控制，Magentic-One 用协调者规划复杂任务。TerminationCondition 根据文本、最大消息、handoff 或组合条件终止；代码执行通过隔离 executor 扩展。

### R — Result

对消息式 Agent 社会的抽象丰富，适合模拟和开放任务。风险是群聊历史迅速膨胀，Selector 可能反复选错 Agent，代码执行风险高，分布式消息带来至少一次投递与幂等问题；维护状态影响长期选型。

## 完整执行流

1. 注册 Agent、工具和 runtime/topic。
2. 创建 Team，选择 RoundRobin/Selector/Swarm 等模式。
3. 用户任务成为初始 message。
4. Team manager 决定下一 Agent；Agent读取上下文并产出 message/tool call/handoff。
5. 工具执行结果作为新 message 进入队列。
6. termination condition 每轮检查；未满足则继续。
7. 结束后返回 transcript 和最终产物；分布式模式还需持久化和重放。

## 难点

| 难点 | 级别 | 做法 | SmileBoss 选择 |
|---|---|---|---|
| 发言选择 | P1 | 多 team pattern | 面试主流程不用自由 selector，采用状态条件 |
| 终止 | P1 | termination conditions | 业务条件优先于模型说“完成” |
| 代码执行 | P0 | executor | 招聘主流程通常禁用；分析场景只读沙箱 |
| 消息膨胀 | P2 | context management | 结构化 ledger + 摘要，原证据外置 |
| 维护方向 | P2 | 官方迁移路线 | 新实现优先看 MAF/LangGraph |

## SmileBoss 借鉴

参考 termination condition、handoff 和分层 runtime；不要让面试 Agent 通过自由群聊决定评分发布。若实现 Agent 间消息，消息必须含 schema/version/trace/idempotency，不把完整候选 PII 广播到公共 topic。

## 评分

架构学习 5/5；生产成熟度 4/5；招聘相关性 3/5；接入成本 2/5；作为模式库研究，谨慎作为新基座。

