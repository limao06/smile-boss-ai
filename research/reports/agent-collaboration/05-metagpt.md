# 05. MetaGPT：SOP 驱动的虚拟软件团队与结构化产物

## 项目卡片

- GitHub：[FoundationAgents/MetaGPT](https://github.com/FoundationAgents/MetaGPT)
- 本地源码：`research/source-code/agent-text2sql/05-metagpt`
- 锁定版本：`11cdf466d042`
- 定位：以“Code = SOP(Team)”组织 PM、架构师、项目经理、工程师等角色。
- 一句话判断：MetaGPT 真正可迁移的不是角色名称，而是用标准作业程序和结构化文档降低 Agent 之间的自然语言损耗。
- 重要性：P1/P2。

## STAR

### S — Situation

一句产品需求直接交给“程序员 Agent”会遗漏需求、接口和测试；多个角色如果只看聊天记录，信息在交接中逐渐变形。

### T — Task

把软件团队的 SOP 编成角色、Action、Message、Environment 和观察/反应机制，让每一步产生明确文档供下一角色消费。

### A — Action

用户需求进入 Environment。Product Manager 的 Action 生成 PRD/用户故事；Architect 读取 PRD 产出系统设计、接口和数据结构；Project Manager 形成任务列表；Engineer 根据设计和任务写代码，测试角色检查。Role 通过 `watch` 订阅特定 Message 类型，执行 observe-think-act 并发布新的结构化 artifact；共享环境和 memory 让产物可见。

### R — Result

“需求 -> PRD -> 设计 -> 任务 -> 代码/测试”的信息损耗比开放群聊低，产物也便于审查。风险是前序错误会级联，角色会生成大量看似正式但无证据的文档，固定 SOP 对动态任务可能僵硬。

## 完整执行流

1. 用户 requirement 进入 Team/Environment。
2. PM 观察需求消息，执行 WritePRD，发布 PRD artifact。
3. Architect 订阅 PRD，生成 API/数据结构/模块设计。
4. Project Manager 将设计拆成依赖任务。
5. Engineer 读取指定任务与设计写实现。
6. Reviewer/Test 产生问题，返给对应角色修订。
7. 终止条件满足后交付代码和文档。

## 对 Agent 写作的启发

每个阶段使用专用 Schema：`ResearchBrief -> EvidenceTable -> Outline -> Draft -> FactCheckReport -> FinalReport`。下一 Agent 只依赖正式产物而非整段思维过程。所有事实都携带 source ID；风格编辑不允许新增来源，审稿问题以 issue 对象返回并指定 owner。

## 难点

| 难点 | 级别 | 做法 | 改进 |
|---|---|---|---|
| 交接损耗 | P1 | typed message/action artifact | 给每个 artifact JSON Schema 和版本 |
| 错误级联 | P1 | 后续审查 | 关键 PRD/证据层设独立验收门 |
| 文档膨胀 | P2 | 完整 SOP | 根据风险选择最短流程，不为角色而角色 |
| 动态调整 | P2 | watch/react | 外层状态机允许跳过、回退和人工变更 |
| 权限 | P0 | 通用角色抽象 | Role 不等于 RBAC，工具权限服务端控制 |

## SmileBoss 借鉴

把 AI 面试报告制定为 SOP：岗位 rubric -> 面试计划 -> 题目选择 -> 回答证据 -> 维度评分 -> 合规审计 -> 人工发布。每一步留下结构化 artifact 和版本。不要模仿一个虚拟公司堆十几个 Agent；SmileBoss 每个 Agent 必须对应独立的验证责任。

## 评分

架构学习 5/5；生产成熟度 3/5；招聘相关性 3/5；接入成本 3/5；优先借鉴 SOP 与交付物契约。

