# 07. CAMEL：Role-Playing、Society 与 Workforce

## 项目卡片

- GitHub：[camel-ai/camel](https://github.com/camel-ai/camel)
- 本地源码：`research/source-code/agent-text2sql/07-camel`
- 锁定版本：`32fb32494897`
- 定位：大规模多 Agent 研究框架，覆盖 ChatAgent、RolePlaying、Workforce、工具、记忆、沙箱和数据生成。
- 一句话判断：CAMEL 适合研究多 Agent 社会、任务分解和批量数据生成；对招聘生产系统应选择性使用，避免把模拟行为误当真实组织可靠性。
- 重要性：P2；涉及合成评估数据时 P1。

## STAR

### S — Situation

开放任务需要不同专业能力并行处理，单 Agent 容易受单一提示和上下文限制；同时开发者需要规模化模拟对话、生成训练/评估数据。

### T — Task

提供可组合 ChatAgent、角色初始化、RolePlaying 会话、Workforce 协调者/工作者、工具和记忆，让任务被分解、路由、执行和评价，并可扩展到大量 Agent。

### A — Action

RolePlaying 先生成或明确 task specification，再初始化 assistant/user 角色，通过轮流消息完成任务。Workforce 中 coordinator 分解任务并分派给不同 worker/team，工作者调用工具或模型完成子任务，结果回到协调者整合；critic/evaluator 可检查结果。沙箱隔离高风险执行，memory 为 Agent 提供历史上下文。

### R — Result

协作原语和工具生态丰富，适合实验、合成面试对话与鲁棒性测试。风险是模拟的候选回答不代表真实人群，角色提示会强化刻板印象，大规模 Agent 成本和不可重复性高。

## 完整执行流

1. 规范目标、约束、交付物和预算。
2. Coordinator 将任务拆为有依赖的子任务。
3. 按能力注册 worker，并只赋予必要工具。
4. 子任务通过 channel 分派；worker 执行并返回结构化结果。
5. Critic 检查证据、格式和冲突，失败项返工。
6. Coordinator 合并产物，达到终止条件后输出。
7. 数据生成场景保存角色参数、随机种子、模型版本和过滤结果。

## 难点

| 难点 | 级别 | 风险与控制 |
|---|---|---|
| 任务分解 | P1 | 子任务需可验收，避免 coordinator 输出模糊自然语言 |
| 角色偏差 | P0 | 不按性别/年龄/地域模拟能力；建立偏差测试 |
| 合成数据污染 | P0/P1 | 合成数据明确标识，不能混入真实绩效标签 |
| 工具安全 | P0 | 沙箱、最小权限、网络/文件白名单 |
| 大规模成本 | P2 | 并发与预算上限、缓存、抽样执行 |

## SmileBoss 借鉴

用 CAMEL 类思路生成面试测试场景：完整回答、含糊回答、矛盾回答、提示注入、拒答和网络中断，用于验证面试状态机，而不是训练自动淘汰模型。Workforce 模式适合批量离线评估，不适合实时面试的核心控制。

## 评分

架构学习 4/5；生产成熟度 3/5；招聘相关性 3/5；接入成本 2/5；用于仿真、红队和离线评估。

