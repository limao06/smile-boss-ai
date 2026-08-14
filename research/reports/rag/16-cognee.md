# 16. Cognee：向量、图谱、本体与 Agent 长期记忆

## 项目卡片

- GitHub：[topoteretes/cognee](https://github.com/topoteretes/cognee)
- 本地源码：`research/source-code/rag/16-cognee`
- 锁定版本：`4b9dd362625d`
- 定位：面向 Agent 的长期记忆/知识引擎，组合向量、知识图和 ontology。
- 一句话判断：Cognee 把记忆视为有生命周期的资产，而非无限追加聊天记录；这对跨轮 AI 面试和长期候选互动很重要。
- 重要性：P0/P1。

## STAR

### S — Situation

Agent 的对话历史越来越长，直接把全部消息重新放入上下文成本高且容易遗忘关键事实；招聘场景还要求知道哪些信息来自简历、哪条来自某次面试、什么时候应删除。

### T — Task

把数据处理为可检索、有关联、有本体约束的长期记忆，支持 remember/recall/forget/improve，并隔离不同用户和数据集。

### A — Action

数据进入后经过 cogniﬁcation：切分、实体/关系抽取、向量化并写入图与向量存储。搜索可以按 chunks、graph、自然语言等策略召回。memory API 将新增信息、回忆、遗忘和改进变为显式操作，数据集/租户用于隔离；追踪帮助知道某次回忆来自何处。

### R — Result

Agent 可以从结构化长期记忆中回忆，而不是依赖无限 history；关系检索也能跨多轮串联事实。风险是记忆抽取可能把模型推断固化成“事实”，遗忘需要同时清理向量、图和缓存，错误记忆会影响后续面试评价。

## 完整执行流

1. 接收数据和 owner/dataset/source/version。
2. 解析、切块、抽取实体关系、生成向量。
3. 写入图和向量索引，保留来源映射。
4. Agent 发起 recall，系统按策略检索相关记忆。
5. 将有限证据注入当前任务；新事实以明确来源写回。
6. improve 流程重建/增强知识；forget 按所有派生层删除。

## 难点

| 难点 | 级别 | 做法 | SmileBoss 控制 |
|---|---|---|---|
| 事实/推断混淆 | P0 | 图与来源 | `fact_type` 强制区分自述、观察、评分、推断 |
| 遗忘权 | P0 | forget 生命周期 | 建删除清单和可验证 tombstone |
| 租户隔离 | P0 | dataset/tenant | 服务端 ACL + Qdrant payload filter |
| 记忆污染 | P1 | improve/重建 | 禁止从未审核生成文本反向成为高可信事实 |
| 时间性 | P1 | 通用元数据 | 每条记忆加 valid_from/to 和 superseded_by |

## SmileBoss 借鉴

AI 面试的长期状态用“证据账本”而不是摘要：每条能力证据关联题目、回答时间段、转写文本、rubric 和置信度。候选人修改简历时，新版本 supersede 旧事实；撤回授权时能完整 forget。图记忆可以用于题目覆盖关系，但招聘决策输入必须限定到当前授权和有效版本。

## 评分

架构学习 4/5；生产成熟度 3/5；招聘相关性 4/5；接入成本 2/5；重点借鉴记忆生命周期和来源模型。

