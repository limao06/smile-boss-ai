# SmileBoss AI 智能招聘系统

> 本仓库用于架构研究、课程演示和二次开发学习，不应未经安全加固直接用于真实招聘决策或生产环境。仓库不包含任何真实 API Key、数据库密码、钉钉 Webhook 或私钥；所有外部凭据必须通过环境变量或 Secret Manager 注入。

> 全部设计方案、全部难点、此前需求追踪和 40 个开源项目研究的统一入口：
> [docs/00-SmileBoss-AI-全部设计文档总索引.md](docs/00-SmileBoss-AI-全部设计文档总索引.md)
>
> 完整的系统架构、逐模块运行流程、数据库与 API、RAG/Agent/Text-to-SQL 研究索引、代码整洁度治理和扩展手册，请先阅读：
> [docs/SmileBoss-AI-智能招聘与多Agent平台-完整设计实现手册.md](docs/SmileBoss-AI-智能招聘与多Agent平台-完整设计实现手册.md)

第一版可运行代码，核心能力包括：

- 职位和候选人管理
- PDF/DOCX/TXT 简历上传与自动文本提取
- 简历结构化与完整度检查
- 候选人岗位推荐
- 候选人 AI 模拟面试
- 企业正式 AI 初面（邀请、问答、证据化报告、HR 审核）
- 账号行为采集与活跃度分析
- 多 Agent 协作平台（Agent/工作流版本、Artifact、检查点、人工审批、运行追踪）
- 受治理的 Text-to-SQL 智能问数（语义模型、SQL 白名单、风险分级、EXPLAIN、人工审核、审计）
- 钉钉人机审核通知（自定义机器人加签、可靠 Outbox、失败退避、脱敏深链）
- OpenAI-compatible 多模型路由（Claude → Qwen → DeepSeek → Kimi）

## 目录

```text
backend/             Spring Boot 3.2.5 / Java 17 多模块后端
frontend/admin/      招聘管理端（Vue 3 + Element Plus）
frontend/candidate/  候选人端（Vue 3 + Element Plus）
docs/                设计说明
```

## 快速启动

后端默认使用 `dev` Profile（H2 内存数据库），无需外部服务即可体验：

```powershell
cd backend
./mvnw.cmd -pl smile-app -am spring-boot:run
```

切换到 MySQL、Redis、Qdrant 和可选模型服务：

```powershell
# 首次执行 backend/sql/init-mysql.sql
$env:SPRING_PROFILES_ACTIVE = 'mysql'
$env:JWT_SECRET = '请使用密码管理器生成至少32字节的随机值'
$env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/smile_boss_ai?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
$env:DB_PASSWORD = '你的数据库密码'
$env:REDIS_PASSWORD = '你的 Redis 密码'
$env:CLAUDE_BASE_URL = '你的 OpenAI-compatible 网关地址'
$env:CLAUDE_API_KEY = '你的 Claude 兼容网关 Key'
$env:DASHSCOPE_API_KEY = '你的 DashScope Key'
./mvnw.cmd -pl smile-app -am spring-boot:run
```

前端：

```powershell
cd frontend/admin
npm.cmd install
npm.cmd run dev

cd ../candidate
npm.cmd install
npm.cmd run dev
```

默认演示账号：`admin/admin123`、`candidate/demo123`。这些账号只用于本地 H2 演示，生产环境必须删除或禁用，并改用密码哈希、强密码和正式身份系统。

## 配置原则

公开仓库中的数据库、Redis 和 Qdrant 默认地址全部指向 `127.0.0.1`。模型 Key、数据库密码、JWT 密钥和钉钉配置只从环境变量注入；可以复制根目录的 `.env.example`，但不得提交填入真实值后的 `.env`。招聘数据使用独立数据库 `smile_boss_ai`。

## 多 Agent 协作平台

后端已内置第一版 SmileAgent 工作流引擎和“候选人综合报告”示例流程，支持 Agent/工作流版本、逻辑并行与 JOIN、结构化 Artifact、有限重试、预算、检查点、人工审批和恢复。招聘管理端左侧新增“Agent 协作平台”，可查看 Agent、工作流、运行轨迹、Artifact/Event 和处理人工审批。

详细接口、示例请求、运行流程和当前 MVP 边界见：`docs/multi-agent-platform.md`。

## 智能问数与人工审核

管理端左侧“智能问数”支持用自然语言查询招聘聚合数据。系统不会直接执行模型输出，而是依次进行候选 SQL 留痕、表级白名单、单语句/只读校验、敏感字段识别、数据库 `EXPLAIN`、默认 `LIMIT`、查询超时和结果审计。L2 敏感查询会自动暂停并进入人工审核，批准时校验 SQL 快照后才执行。

全新 MySQL 环境执行最新版 `backend/sql/init-mysql.sql`。早期数据库先执行 `backend/sql/migrate-v2-hitl-text-to-sql.sql`，再执行最新版初始化脚本。详细设计和 API 示例见 `docs/text-to-sql-hitl.md`。

## 钉钉人机审核通知

钉钉接入复用通用的 `DINGTALK_WEBHOOK`、`DINGTALK_SECRET`、`DINGTALK_KEYWORD` 环境变量。系统通过 Outbox 异步发送脱敏审批通知，HR 点击深链后仍需登录管理端完成有审计的审批。默认关闭，已有数据库需执行 `backend/sql/migrate-v3-dingtalk-hitl.sql`。完整配置、安全边界、状态机和排障手册见 [docs/dingtalk-hitl-integration.md](docs/dingtalk-hitl-integration.md)。

## 公开仓库安全说明

- `.env`、证书、私钥、构建产物、上传文件、IDE 配置和第三方研究源码目录均已加入 `.gitignore`。
- `dev` Profile 未配置 `JWT_SECRET` 时会在每次启动时生成随机的进程级签名密钥；重启后旧令牌自动失效。
- `mysql` 等非 dev 环境没有 `JWT_SECRET` 时会拒绝启动，不会退回到公开的固定默认密钥。
- `.env.example` 只包含变量名、空值和 localhost 示例，不包含可用凭据。
- 如果怀疑凭据曾经进入 Git 历史，仅删除当前文件不够；应立即吊销凭据并重写历史。
