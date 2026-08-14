# 03. AWS Interview Assistant：面试题作为原子知识对象

## 项目卡片

- GitHub：[aws-samples/sample-hr-assistant-with-rag](https://github.com/aws-samples/sample-hr-assistant-with-rag)
- 本地源码：`research/source-code/rag/03-aws-interview-assistant`
- 锁定版本：`46911edb0044`
- 技术定位：Amazon Bedrock Knowledge Bases + S3 Vectors 的面试题检索助手。
- 一句话判断：这是 20 个 RAG 项目里对“新增 AI 面试”最直接的参考，其关键不是 AWS，而是“一道题就是一个完整知识对象，并带评分标准和提问理由”。
- 重要性：P1；题目检索、评分依据和追问上下文决定面试质量。

## STAR 分析

### S — Situation

AI 面试需要根据岗位、难度和能力维度选题，同时让模型知道怎样提问、预期回答包含什么、如何评分、为何选择该题。若将整本题库按字符任意切块，问题、答案和评分标准可能被切开，生成端会拿到不完整证据。

### T — Task

建立可过滤、可语义搜索、可解释的题库；给定岗位与面试上下文后返回少量完整题目，并保持题目、面试官指引、评价标准、期望答案和时间预算一致。

### A — Action

每道题保存为独立 Markdown 文件，内容包含题干、interviewer instruction、evaluation criteria、expected answer、预计时间和选题原因；伴随 metadata 记录 category、difficulty 等过滤字段。由于文档本身已是原子单元，知识库使用 `NONE` 切块策略，避免破坏内部结构。嵌入采用 Titan Embed Text v2、1024 维；查询时先按类别/难度做元数据约束，再用 hybrid retrieval 取 top 3，生成面试建议。

### R — Result

检索结果天然可直接用于提问和评分，引用粒度清楚，top-3 也能控制模型上下文。局限是系统更多展示题库 RAG，尚未完整解决实时语音、候选回答状态、动态追问、跨轮证据积累和公平性审计。

## 完整执行流程

1. 题库作者按固定模板创建一道题一个 Markdown。
2. 为题目附加 category、difficulty、role、skills、language 等 metadata。
3. 上传对象存储并同步知识库；不再二次字符切分。
4. 面试规划器根据岗位画像、已覆盖能力和剩余时间构造检索请求。
5. 先应用硬过滤，例如 Java/高级/系统设计，再做混合语义+关键词召回。
6. 返回 top-3 完整题目，规划器选择其中一道并记录选题原因。
7. 提问 Agent 按 interviewer instruction 表达题目，但不泄露 expected answer。
8. 候选人作答后，评分 Agent 读取 evaluation criteria 和 expected answer，提取回答证据。
9. 追问 Agent根据缺失证据生成澄清问题；达到最大追问数后终止。
10. 将题目版本、回答、评分 rubric、证据和模型版本完整留痕。

## 知识对象设计

```text
InterviewQuestion
├── question                 # 给候选人的题干
├── interviewer_instruction  # 怎样问、何时追问
├── evaluation_criteria      # 分维度评分规则
├── expected_evidence        # 期待听到的事实/推理，不等于唯一标准答案
├── time_budget              # 题目与追问时间
├── selection_reason         # 为什么适合该岗位/轮次
└── metadata                 # 岗位族、技能、难度、语言、版本、合规标签
```

## 难点与重要性

| 难点 | 级别 | 项目做法 | SmileBoss 应增强 |
|---|---|---|---|
| 题目结构不被切碎 | P1 | 一题一文档、NONE chunking | 保持原子对象，同时为长 case 题建立父子块 |
| 精确选题 | P1 | metadata + hybrid top-3 | 增加已问题去重、覆盖度和时间预算约束 |
| 评分一致性 | P0 | criteria + expected answer | rubric 版本化、双评审/校准、禁止敏感属性 |
| 动态追问 | P1 | 样例未完全覆盖 | 用状态图判断“证据充分/矛盾/需澄清” |
| 面试安全 | P0 | 基础云权限 | 身份、录音同意、数据保留、人工申诉必须独立设计 |

## 对 SmileBoss 的使用建议

- 直接借鉴：题目原子化、metadata 预过滤、题目内嵌评分 rubric、完整引用。
- 模型和数据库无需照搬 AWS：用现有 Qdrant 存 1024 维 Qwen `text-embedding-v3`；MySQL 存题目主数据和版本；Redis 存面试实时状态；Claude 主评审，Qwen/DeepSeek/Kimi 按现有降级链路使用。
- 面试状态必须包含 `covered_competencies`、`remaining_time`、`asked_question_ids`、`evidence_ledger`、`follow_up_count`、`risk_flags`，不能仅依赖聊天历史。
- 不建议照搬：top-3 直接自动出题而无规划器；用 expected answer 做关键词匹配；把方言、停顿、语速直接解释为能力。

## 评分

架构学习 5/5；生产成熟度 3/5；招聘相关性 5/5；接入成本 4/5；是 SmileBoss AI 面试题库与评分证据层的首要参考。

