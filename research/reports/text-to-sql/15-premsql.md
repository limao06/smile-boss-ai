# 15. PremSQL：本地优先的小模型生成、执行纠错与微调

## 项目卡片

- GitHub：[Anindyadeep/text2sql](https://github.com/Anindyadeep/text2sql)
- 本地源码：`research/source-code/agent-text2sql/15-premsql`
- 锁定版本：`b656dc04142b`
- 定位：本地优先 Text-to-SQL 工具链，包含 generators、executors、evaluators、agents 及 LoRA/QLoRA 微调。
- 一句话判断：PremSQL 的价值是证明小模型也能通过正确 Schema 上下文、执行反馈和领域微调完成受控数据任务，适合隐私敏感环境。
- 重要性：P1；执行安全 P0。

## STAR

### S — Situation

将 Schema 和问题发给外部大模型可能不符合数据政策；通用大模型成本高，且在固定招聘库上的 SQL 风格未必稳定。小模型一次生成准确率不足，需要执行反馈。

### T — Task

允许本地模型读取数据库上下文生成 SQL，执行后根据错误自我修正；提供评估、报告/图表 Agent，并支持 LoRA/QLoRA 领域微调。

### A — Action

系统准备数据库和 Schema 上下文，由 generator 适配不同本地/开放模型，生成 SQL。Executor 在目标或测试数据库运行，错误信息进入 correction 流程；Evaluator 比较生成结果。Agent 可基于查询结果继续生成报告或图表。训练模块用领域问答 SQL 进行参数高效微调。

### R — Result

数据和模型可以本地化，推理成本可控，执行纠错提高实用性。风险是小模型上下文和复杂推理能力有限，微调数据质量要求高，执行错误修复可能变成无限循环，模型本地部署也需 GPU/运维。

## 完整执行流

1. 读取经权限裁剪的 Schema、列描述和方言。
2. 检索少量示例，构建 prompt。
3. 本地 generator 输出 SQL。
4. 语法/AST/权限验证。
5. 在只读沙箱执行并捕获最小错误。
6. 将错误类型和相关 Schema 回给模型，最多 N 次修复。
7. 验证结果形状，返回 SQL/数据/解释。
8. 离线 evaluator 统计执行准确率；高质量修正可进入微调集。

## 难点与 SmileBoss 借鉴

| 难点 | 级别 | 建议 |
|---|---|---|
| 本地隐私 | P0 | 模型本地不等于权限安全，仍需行列裁剪 |
| 微调数据 | P1 | 只用经审核、去敏、覆盖方言的样本 |
| 修复循环 | P1 | 错误分类、最大次数、同错停止 |
| 模型评估 | P1 | 按简单/连接/聚合/时间/嵌套分层 |
| 部署成本 | P2 | 先用现有模型网关积累数据，再判断微调收益 |

SmileBoss 当前可先沿用 Claude/Qwen/DeepSeek/Kimi，不急于本地微调。等积累数千条审核 SQL 后，再用 PremSQL 类路线评估专用模型，且保留同一安全执行层。

## 评分

架构学习 4/5；生产成熟度 3/5；招聘相关性 3/5；接入成本 3/5；本地化与微调的中期参考。

