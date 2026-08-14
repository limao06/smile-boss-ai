# RAG、Multi-Agent 与 Text-to-SQL 开源项目源码研究

> SmileBoss 全部系统设计、全部难点与此前需求的统一文档入口：
> [../docs/00-SmileBoss-AI-全部设计文档总索引.md](../docs/00-SmileBoss-AI-全部设计文档总索引.md)

> 研究基线日期：2026-08-14  
> 研究对象：20 个 RAG 项目、10 个多 Agent/协作写作项目、10 个 Text-to-SQL 项目  
> 第三方源码不随本仓库发布；请根据清单中的 GitHub 地址和锁定提交自行克隆到可选的 `research/source-code`。该目录已被 `.gitignore` 排除。

## 先读什么

1. [研究方法与 STAR 分析框架](reports/00-research-method-and-star-framework.md)：解释为什么这样选项目、怎样从源码还原执行流，以及如何阅读每份报告。
2. [40 个仓库清单与版本锁定](REPOSITORY_MANIFEST.md)：包含分类、GitHub 地址、本地路径、分支、提交号和定位。
3. [横向比较与 SmileBoss 落地设计](reports/COMPARISON_AND_SMILEBOSS_GUIDE.md)：把 40 个项目的优点组合成招聘系统可实施的 RAG、Agent、AI 面试和 Text-to-SQL 架构。
4. `reports/rag/`：20 个 RAG 项目逐项源码分析。
5. `reports/agent-collaboration/`：10 个 Agent 协作、内容写作与研究型项目逐项分析。
6. `reports/text-to-sql/`：10 个 Text-to-SQL 项目逐项分析。

## 研究结论速览

- 招聘 RAG 不是“把整份简历切块后向量搜索”。正确路线是：**结构化解析 + 多粒度证据单元 + 混合召回 + 父子块回填 + 重排 + 规则/图谱约束 + 可追溯引用**。
- AI 面试不是单 Agent 连续提问。生产级方案需要：**面试规划 Agent、提问 Agent、证据检索 Agent、追问 Agent、评分 Agent、偏差/合规审计 Agent**，并以状态机控制轮次、超时和终止条件。
- Agent 协作的核心不是角色数量，而是：**共享状态契约、明确交接物、有限循环、失败恢复、检查点、人工审批与可观测性**。
- Text-to-SQL 的难点不是“生成 SQL 字符串”，而是：**语义层、Schema 裁剪、值检索、示例 RAG、候选生成、执行验证、安全沙箱、权限裁剪和结果解释**。
- SmileBoss 当前的 Java 17 / Spring Boot / MySQL / Redis / Qdrant / Qwen Embedding / Claude-Qwen-DeepSeek-Kimi 模型配置可以直接承载这一方案，不需要换数据库或模型供应链。

## 目录结构

```text
research/
├── README.md
├── REPOSITORY_MANIFEST.md
├── reports/
│   ├── 00-research-method-and-star-framework.md
│   ├── COMPARISON_AND_SMILEBOSS_GUIDE.md
│   ├── rag/                       # 20 份
│   ├── agent-collaboration/       # 10 份
│   └── text-to-sql/               # 10 份
└── source-code/                   # 可选的本地第三方源码目录（Git 忽略）
    ├── rag/
    └── agent-text2sql/
```

## 使用源码时的注意事项

- 所有仓库均以浅克隆方式下载，足够阅读当前实现，但不包含完整 Git 历史。
- 研究报告锁定的是清单里的提交号；未来上游项目变化时，报告中的文件名或流程可能发生变化。
- “可借鉴”不等于可以直接复制。请逐仓库检查许可证，尤其是商用、分发、模型权重和训练数据条款。
- `TaskWeaver`、`Vanna` 等仓库在研究日期前后已归档或进入维护状态，报告会明确指出其历史价值与采用风险。
- 报告中的评分是针对 **SmileBoss 智能招聘系统** 的适用性评分，不代表项目的绝对质量。
