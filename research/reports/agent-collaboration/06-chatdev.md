# 06. ChatDev：YAML 工作流、虚拟公司与可配置协作拓扑

## 项目卡片

- GitHub：[OpenBMB/ChatDev](https://github.com/OpenBMB/ChatDev)
- 本地源码：`research/source-code/agent-text2sql/06-chatdev`
- 锁定版本：`4fb2db0ea903`
- 定位：ChatDev 2.0 提供 YAML 定义的 Agent workflow/runtime/server；经典版本模拟 CEO、CTO、程序员和测试员的软件公司。
- 一句话判断：其价值从“角色扮演写软件”演进为可配置工作流，适合研究循环、投票、人工节点和工具/MCP 接入；生产仍需强类型状态与权限边界。
- 重要性：P2。

## STAR

### S — Situation

Agent 协作拓扑经常变化：有些任务顺序执行，有些并行，有些需要投票、循环修订或人工介入。如果拓扑写死在代码中，试验成本高；如果完全由模型自由组织，则难以复现。

### T — Task

用 YAML 描述 Agent、节点、边、工具和运行参数，由 runtime 实例化并调度；同时支持 graph、loop、voting、human、memory 和 MCP 等协作能力。

### A — Action

系统解析 YAML，构造模型、Agent、工具和节点。调度器按图触发节点，将产物/消息传递给后继节点；loop 节点根据条件迭代，voting 汇集多个 Agent 意见，human 节点暂停等待输入，memory 保存跨步骤上下文。经典 ChatDev 则将软件开发拆为 design/coding/test 等 chat chain，并用“communicative dehallucination”通过追问澄清模糊指令。

### R — Result

流程易配置、适合演示不同协作模式。风险是 YAML 静态检查能力有限、秘密和权限配置容易泄漏、自然语言投票不等于真实质量保证；角色之间长对话仍可能造成上下文膨胀。

## 完整执行流

1. 加载 YAML 并做 Schema/引用/循环合法性校验。
2. 实例化 Agent、model provider、tools、memory。
3. 输入进入起始节点；节点读取声明的输入变量。
4. 节点执行并生成 artifact/message。
5. Scheduler 根据 edge、router、loop 或 vote 决定后继。
6. human 节点持久化状态并暂停，恢复后继续。
7. 达到输出节点或预算上限，保存产物和 trace。

## 难点与 SmileBoss 借鉴

| 难点 | 级别 | 建议 |
|---|---|---|
| 配置安全 | P0 | 工作流模板白名单，配置不能授予新权限 |
| 循环 | P1 | 最大轮数、超时、无变化检测 |
| 投票 | P1 | 多样化模型/证据，不能把同一模型三次输出当独立意见 |
| 人工恢复 | P1 | 状态持久化、审批身份和审计 |
| 版本治理 | P1 | 每次 run 绑定 workflow hash |

SmileBoss 可为“候选报告生成”和“职位文案协作”提供管理员可选模板，但正式面试流程不可让普通用户任意上传 YAML。投票只用于低风险内容选择，录用/淘汰仍是人工业务决定。

## 评分

架构学习 4/5；生产成熟度 2/5；招聘相关性 3/5；接入成本 3/5；作为工作流配置与协作模式参考。

