# SmileBoss AI 全部设计文档总索引

> 这是整个项目文档的唯一总入口。  
> 更新基线：2026-08-24
> 项目根目录：克隆后的仓库根目录  
> 第三方源码：按 `research/REPOSITORY_MANIFEST.md` 自行克隆；`research/source-code` 不纳入本仓库

## 1. 文档包总览

```text
docs/
├── 00-SmileBoss-AI-全部设计文档总索引.md
├── SmileBoss-AI-智能招聘与多Agent平台-完整设计实现手册.md
├── 40个RAG-Agent协作-Text-to-SQL开源项目全量设计汇编.md
├── multi-agent-platform.md
├── text-to-sql-hitl.md
├── dingtalk-hitl-integration.md
├── AI简历工作台-设计实现与本次更新说明.md
└── API.md

research/
├── README.md
├── REPOSITORY_MANIFEST.md
└── reports/
    ├── 00-research-method-and-star-framework.md
    ├── COMPARISON_AND_SMILEBOSS_GUIDE.md
    ├── rag/                    # 20 份
    ├── agent-collaboration/    # 10 份
    └── text-to-sql/            # 10 份
```

## 2. 必读文档

### 2.1 系统全部设计、难点和代码实现

[SmileBoss AI 智能招聘与多 Agent 平台——完整设计、实现与代码导读](SmileBoss-AI-智能招聘与多Agent平台-完整设计实现手册.md)

包含：

- 此前全部需求的追踪矩阵；
- 产品角色和权限边界；
- 智能招聘总体架构；
- 简历自动解析、完整度、推荐、模拟面试、正式面试、活跃度；
- 多 Agent 定义、工作流、Artifact、Checkpoint 和 HITL；
- Text-to-SQL 生成、验证、风险、审批和执行；
- 招聘 RAG 的事实模型、Qdrant payload、父子块、RRF 和评估；
- Agent 协作写作、状态、并发 Worker 和交接契约；
- Text-to-SQL 语义层、上下文 RAG、澄清、AST、候选和结果 UT；
- 事务、幂等、补偿和 Outbox；
- 故障、降级和恢复矩阵；
- 数据安全、隐私、公平和提示注入；
- 性能、容量、SLO、缓存和连接池；
- 日志、指标、追踪和告警；
- 代码整洁之道和阿里巴巴 Java 规范治理；
- 部署拓扑、迁移、灰度、回滚、测试和生产验收；
- 实施路线、团队分工、ADR 和术语表。

### 2.2 40 个开源项目连续汇编

[40 个 RAG、Agent 协作与 Text-to-SQL 开源项目全量设计汇编](40个RAG-Agent协作-Text-to-SQL开源项目全量设计汇编.md)

每个项目都包含：

- GitHub、本地源码和锁定提交；
- STAR 背景；
- 完整执行流程；
- 核心数据/组件设计；
- P0/P1/P2 难点；
- 为什么重要；
- SmileBoss 直接采用、改造采用或明确不采用的部分；
- 独立源码研究底稿链接。

### 2.3 40 份独立源码底稿

[研究总入口](../research/README.md)

独立底稿提供比连续汇编更细的源码入口、配置、实现步骤、残余风险和评分。仓库清单、提交号和 GitHub 地址见：[REPOSITORY_MANIFEST.md](../research/REPOSITORY_MANIFEST.md)。

### 2.4 钉钉人机审核专项

[SmileBoss 人机审核接入钉钉：完整设计、配置与运维手册](dingtalk-hitl-integration.md)

包含：`dify-test` 配置对照、STAR 分析、加签算法、完整执行流程、Outbox 状态机、多实例抢占、失败退避、宕机恢复、消息脱敏、安全深链、数据库迁移、生产配置、排障和互动卡片/OA 升级边界。

### 2.5 AI 简历工作台专项

[AI 简历工作台：设计、实现与本次更新说明](AI简历工作台-设计实现与本次更新说明.md)

包含：在线主简历、不可变版本、PDF/文本导入安全合并、导入预览确认、字段级优化、岗位定制简历、可打印导出、证据化招呼语、对象级权限、MySQL v4 迁移、测试结果和生产化边界。

## 3. 按角色阅读

### 产品经理/项目负责人

1. 主手册第 1–3 节：目标、STAR 和架构；
2. 第 21–22 节：全部需求和角色边界；
3. 第 36–38 节：验收、路线、团队和 ADR；
4. 开源汇编第 42–45 节：最终组合和不采用项。

### 后端开发

1. 主手册第 4 节：代码地图；
2. 第 5–11 节：现有业务运行；
3. 第 23、25–28 节：分层、Agent、SQL、一致性和故障；
4. 第 32、34–35 节：代码门禁、迁移和测试。

### AI/RAG 工程师

1. 主手册第 24 节：RAG 全量设计；
2. 第 25–26 节：Agent 写作和 Text-to-SQL；
3. 开源汇编第 2–21、32–41 节；
4. `research/reports/` 中对应源码底稿。

### 前端开发

1. 主手册第 3、7、9–13 节；
2. 第 28 节异常/降级；
3. 第 30 节性能；
4. `docs/API.md`；
5. 管理端的 `TextToSqlPanel.vue` 和 `App.vue`。

### 测试/质量

1. 主手册第 28 节故障矩阵；
2. 第 29 节安全；
3. 第 30–31 节 SLO 和可观测性；
4. 第 35–36 节测试分层和验收清单；
5. 开源汇编 IBM Text2SQL Evaluation Toolkit 一节。

### 安全、法务和审计

1. 主手册第 10 节 HITL；
2. 第 27 节一致性和审批；
3. 第 29 节安全、PII 和公平；
4. 第 31 节审计；
5. 第 36 节生产安全验收；
6. 开源汇编 Onyx、R2R、Hiring Agent 和 CHESS 章节。

### 运维/SRE

1. 主手册第 28 节降级；
2. 第 30–31 节容量、SLO 和告警；
3. 第 33–34 节生产拓扑、迁移和回滚；
4. 第 36 节运维验收。

## 4. 按业务能力阅读

| 能力 | 当前实现 | 详细目标设计 | 开源参考 |
|---|---|---|---|
| 简历解析 | 主手册第 5 节 | 第 24.1–24.4 节 | Hiring Agent、RAGFlow |
| AI 简历工作台 | AI 简历工作台专项 | 在线主简历、版本、导入、优化、岗位版本、招呼语 | Hiring Agent、SkillSyncer |
| 完整度 | 第 5.2 节 | 第 21、24.1–24.3 节 | Hiring Agent、SkillSyncer |
| 推荐 | 第 6 节 | 第 24.5–24.6 节 | Resume Screening、Skillspace |
| 模拟面试 | 第 7.2 节 | 第 7.4、24、25 节 | AWS Interview Assistant |
| 正式面试 | 第 7.3 节 | 第 25、27–29 节 | LangGraph、MAF |
| 活跃度 | 第 8 节 | 第 21、29–31 节 | 规则统计为主，不用 RAG 决策 |
| 多 Agent | 第 9 节 | 第 25、27、31–33 节 | LangGraph、MAF、MetaGPT |
| Agent 写作 | 第 9.2 节 | 第 25.2–25.5 节 | STORM、Open Deep Research |
| HITL | 第 10 节 | 第 27–29 节 | LangGraph、MAF |
| 钉钉审核触达 | 钉钉专项手册 | 主手册第 41 节 | `dify-test` 自定义机器人模式 |
| Text-to-SQL | 第 11 节 | 第 26–31 节 | WrenAI、CHESS、IBM Toolkit |

## 5. “当前已经实现”和“生产目标”区分

### 当前已实现

- Spring Boot/H2/MySQL 可运行后端；
- Vue 管理端和候选端；
- 简历文件抽取、规则分析、完整度和模型增强；
- 规则推荐和 AI 解释；
- 模拟/正式面试和 HR 审核；
- 活跃度分析；
- 多 Agent DAG、Artifact、Event、Checkpoint 和 HITL；
- Text-to-SQL 词法 Guard、EXPLAIN、L0–L3、敏感审批和审计；
- 统一人工审核租约、快照和乐观锁；
- 钉钉机器人加签通知、可靠 Outbox、失败退避、宕机恢复和安全审核深链；
- 17 项后端自动化测试；
- 后端 JAR、管理端和候选端生产构建。

### 生产目标但尚未全部实现

- OCR/VLM 和简历版面/证据坐标；
- Qdrant 父子块和混合 RAG 主链；
- 题库/Rubric RAG、有限追问和证据账本；
- Redis Stream 多 Worker 物理并发；
- 完整 JSON Schema validator；
- Text-to-SQL 语义 RAG、AST/ACL/Cost Guard 和结果 UT；
- 独立数据库只读副本/账号；
- 审批超时扫描，以及邮件/短信等其他 Outbox 通道；
- BCrypt/Argon2、Secret Manager 和完整租户权限；
- 对象存储、病毒扫描、备份恢复、灰度和生产监控。

文档中凡是生产目标均使用“建议、目标、下一阶段”表述，不能把它们当作已经完成的代码功能。

## 6. 文档权威顺序

出现描述不一致时，按以下顺序判断：

1. 当前代码和自动化测试；
2. `schema.sql`、`data.sql` 和 MySQL 初始化/迁移脚本；
3. 主手册中标记为“当前实现”的内容；
4. 专项文档；
5. 主手册和开源汇编中的“生产目标”；
6. 独立开源报告中的上游设计。

上游项目的实现不等于 SmileBoss 当前实现；研究报告用于解释选型和演进，不自动成为产品承诺。

## 7. 文档维护规则

代码变更时应同步：

1. 更新或新增自动化测试；
2. 更新 API 或 Schema；
3. 更新主手册对应“当前实现”流程；
4. 若改变架构取舍，新增/修改 ADR；
5. 若升级开源研究基线，记录新提交和研究日期，不覆盖旧结论；
6. 如果功能尚未实现，只能放在目标/路线图，不写入已实现列表。

## 8. 快速验证命令

```powershell
cd <项目目录>\backend
.\mvnw.cmd -pl smile-app -am test
.\mvnw.cmd -pl smile-app -am package -DskipTests

cd <项目目录>\frontend\admin
npm.cmd run build

cd <项目目录>\frontend\candidate
npm.cmd run build
```

## 9. 最终交付说明

本项目的“全部设计文档”不是单纯把对话复制进一个文件，而是整理为三层：

1. **总索引**：告诉不同角色从哪里读；
2. **系统主手册 + 40 项连续汇编**：可以从头到尾理解全部设计和难点；
3. **40 份源码级独立底稿 + 专项文档**：需要核对具体项目、源码入口和 API 时深入阅读。

这样既保留最详细内容，也避免一份超大文件无法导航、无法维护和无法定位来源。
