# 18. RAGs：用自然语言配置 RAG Pipeline

## 项目卡片

- GitHub：[vanna-ai/rags](https://github.com/vanna-ai/rags)
- 本地源码：`research/source-code/rag/18-rags`
- 锁定版本：`4bec27023950`
- 定位：让用户以自然语言生成/调整 RAG 配置的轻量项目。
- 一句话判断：RAGs 的研究价值在“把 pipeline 参数产品化”，但让 LLM 直接决定生产配置会带来可重复性和安全问题。
- 重要性：P2/P3。

## STAR

### S — Situation

业务人员知道自己要“回答更精确、文档更长、需要摘要”，却不知道 chunk size、top-k、embedding 或 prompt 参数。手工改代码使每个知识库都依赖工程师。

### T — Task

把系统提示词、top-k、chunk size、embedding、LLM 和 summarization 等参数暴露为配置，让 Agent 根据自然语言需求生成 pipeline，并选择向量检索或摘要路径。

### A — Action

用户描述目标，配置 Agent 将其转成结构化设置；系统根据配置建索引和问答链。查询时 Agent 可判断更适合 vector retrieval 还是 summarization，再调用相应流程。配置本身成为可保存、比较和复用的对象。

### R — Result

降低原型门槛，便于快速演示不同参数。局限是仓库很轻、缺少生产权限与评估闭环；自然语言配置可能产生不兼容或昂贵参数，结果难以复现。

## 完整执行流

1. 用户描述文档类型和问答目标。
2. LLM 生成结构化 pipeline 配置。
3. 校验允许的模型、范围和参数上下限。
4. 按配置切块、嵌入和建库。
5. 查询路由至向量检索或摘要。
6. 生成回答并保存使用的配置版本。

## 难点与 SmileBoss 选择

| 难点 | 级别 | 建议 |
|---|---|---|
| 配置合法性 | P1 | JSON Schema + 白名单 + 上下限，LLM 只能建议 |
| 可重复性 | P1 | 所有运行锁定 `pipeline_version` |
| 成本失控 | P2 | 预算预估、审批和速率限制 |
| 业务易用性 | P3 | 提供“简历/职位/题库”预设模板而非任意配置 |
| 效果验证 | P1 | 配置发布前自动跑领域评估集 |

SmileBoss 可以做“知识库配置助手”，让 HR 描述需求后生成草案，但只能从经验证的模板中选择；发布必须经过评估和管理员审批。模型不能改变 tenant filter、PII 规则或删除策略。

## 评分

架构学习 2/5；生产成熟度 1/5；招聘相关性 2/5；接入成本 4/5；用于管理界面灵感，不作为核心架构。

