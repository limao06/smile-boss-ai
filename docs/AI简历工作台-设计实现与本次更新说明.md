# SmileBoss AI 简历工作台：设计、实现与本次更新说明

> 文档版本：1.0
> 更新日期：2026-08-24
> 对应能力：在线主简历、PDF/文本导入、版本确认、AI 字段级优化、岗位定制简历、可打印导出、个性化招呼语
> 适用范围：当前仓库的可运行学习版；上线真实招聘环境前仍需完成本文“生产化边界”中的工作

## 1. 本次更新目标与结果

本次更新把原来的“上传文件后得到一次解析结果”升级成完整的候选人简历工作台。系统不再把 PDF 解析、简历优化、岗位推荐和沟通文案当成互不关联的功能，而是建立一份版本化的在线主简历，其他 AI 能力只消费候选人已经确认的事实。

已经实现的端到端能力如下：

1. 候选人首次打开工作台时，根据账号资料初始化在线主简历和 V1 版本。
2. PDF、DOC、DOCX、TXT、Markdown 或粘贴文本继续复用原有解析入口。
3. 第一次文件导入自动创建并发布主简历版本。
4. 已经存在主简历时，新文件只创建待确认版本，不静默覆盖当前版本。
5. 导入版本和当前版本进行安全合并：文件中缺少的字段保留当前值，技能稳定去重合并。
6. 候选人先查看导入版本中的概述、技能、工作、项目和教育内容，再明确确认发布。
7. 在线编辑每保存一次都追加新版本，历史版本保持不可变。
8. 简历质量拆分为综合质量、完整度、事实证据度、表达清晰度和 ATS 可读性。
9. AI 优化返回字段级建议，不返回无法审计的整份覆盖文本。
10. 高风险缺失项只生成 `REQUEST_INPUT` 或 `VERIFY_ONLY`，不自动编造学历、经历、技能和量化结果。
11. 用户选择建议后，系统创建新的 `AI_OPTIMIZATION` 主版本并发布。
12. 选择已发布岗位后，可以一键创建 `TARGETED` 岗位定制版本，不改变当前主简历指针。
13. 岗位版本可以下载 UTF-8 可打印 HTML，在浏览器中直接打印或另存为 PDF。
14. 系统根据当前简历事实和岗位技能交集生成多种招呼语，同时展示事实依据。
15. 招呼语只供候选人确认、编辑和复制，不会自动群发。
16. 候选人简历接口增加对象级访问控制，不能通过修改 `candidateId` 访问其他候选人的简历。
17. 新增完整集成测试，覆盖初始化、编辑、导入待确认、发布、优化、岗位生成、导出、招呼语和越权拦截。

## 2. 架构原则

### 2.1 在线主简历是唯一事实源

候选人账号资料、文件解析和手动编辑最终都汇入 `candidate_resume_workspace`。岗位推荐、简历优化、岗位定制和招呼语不直接读取未经确认的模型推断，而是读取当前发布的 `MASTER` 版本。

```text
候选人资料 ─┐
PDF/DOCX ───┼─> 解析与规则校验 ─> 待确认版本 ─> 在线主简历
粘贴文本 ───┘                              │
                                           ├─> AI 字段级优化
                                           ├─> 岗位定制版本
                                           ├─> 可打印简历
                                           └─> 个性化招呼语
```

### 2.2 版本追加，不原地覆盖

`candidate_resume_workspace.current_version_id` 只保存当前发布主版本的指针。实际内容保存在 `candidate_resume_version.content_json`，每次导入、手动保存和应用 AI 建议都插入新行。

这样可以回答：

- 用户当前使用哪一版简历；
- 某次修改来自手动编辑、PDF、文本还是 AI；
- 某次岗位沟通使用哪一版简历；
- AI 修改前后是什么内容；
- 出现问题时应该回到哪个版本。

### 2.3 模型提供候选文案，代码控制事实和状态

当前 LLM 只参与两类低副作用工作：

- 在有证据的前提下重写个人概述；
- 根据岗位和已确认技能生成招呼语候选文案。

以下决策全部由 Java 代码处理：

- 质量分数；
- 版本号；
- 当前版本指针；
- 用户权限；
- 是否允许发布；
- 哪些建议可以应用；
- 模型失败后的规则降级；
- 文件大小和文件类型校验；
- 招呼语最大长度；
- 数据库存储和审计。

## 3. 核心执行流程

### 3.1 初始化在线主简历

请求：

```http
GET /api/candidates/{candidateId}/resume-workspace
```

执行步骤：

1. `CandidateResumeAccessGuard` 检查当前账号是否有权访问候选人。
2. 查询 `candidate_resume_workspace`。
3. 如果不存在，读取 `talent_candidate` 中的基本资料。
4. 构造 `ResumeContent`。
5. 创建工作台。
6. 创建 `PROFILE_INITIALIZATION`、`MASTER`、V1 版本。
7. 把 V1 设置为当前版本。
8. 返回当前版本、质量分和全部版本历史。

### 3.2 PDF/文本自动添加到在线简历

原有接口保持兼容：

```http
POST /api/resumes/upload
POST /api/resumes/parse-text
```

新流程为：

```text
文件扩展名检查
→ 20MB 大小检查
→ Tika MIME 检测
→ 文本提取
→ 清洗空字符和多余空白
→ 候选人范围内文件哈希去重
→ 确定性规则结构化
→ 可选 LLM 增强
→ 原始解析记录落库
→ 构造 ResumeContent
→ 与当前主简历安全合并
→ 创建新版本
→ 首次导入自动发布 / 非首次等待确认
```

安全合并规则：

| 字段情况 | 处理方式 |
|---|---|
| 导入字段有内容、当前为空 | 使用导入内容 |
| 导入字段为空、当前有内容 | 保留当前内容 |
| 两边都有内容 | 待确认版本暂用导入内容，用户预览后决定是否发布 |
| 技能列表 | 当前技能和导入技能按顺序去重合并 |
| 原始证据文本 | 使用最新导入文本 |

返回值新增：

```json
{
  "workspaceImport": {
    "workspaceId": 1,
    "versionId": 3,
    "publishStatus": "AWAITING_CONFIRMATION",
    "requiresConfirmation": true,
    "quality": {}
  }
}
```

### 3.3 手动编辑在线简历

```http
PUT /api/candidates/{candidateId}/resume-workspace
```

请求体包含完整 `ResumeContent` 和变更摘要。服务端不更新旧版本，而是：

1. 生成下一个版本号；
2. 插入 `MANUAL_EDIT` 主版本；
3. 更新当前版本指针；
4. 把姓名、联系方式、城市、工作年限、求职意向、技能和概述投影回 `talent_candidate`；
5. 返回新的工作台快照。

### 3.4 AI 字段级优化

```http
POST /api/candidates/{candidateId}/resume-workspace/optimize
```

规则服务先生成确定性建议，模型可在配置启用时提供个人概述候选文案。建议结构如下：

```json
{
  "id": "SUMMARY_REWRITE",
  "section": "个人概述",
  "field": "summary",
  "operation": "REPLACE",
  "before": "原文",
  "after": "建议文案",
  "reason": "突出已确认的工作年限、目标岗位和核心技能",
  "riskLevel": "MEDIUM",
  "autoApplicable": false,
  "evidence": "工作年限、求职意向和技能来自在线主简历"
}
```

支持的操作含义：

| 操作 | 含义 | 能否直接应用 |
|---|---|---|
| `REPLACE` | 有明确前后文本的字段替换建议 | 用户勾选后可以 |
| `REQUEST_INPUT` | 缺少事实，需要用户补充 | 不可以 |
| `VERIFY_ONLY` | 岗位要求中出现、简历中没有，需核实 | 不可以 |

当前高风险规则包括：

- 缺少工作经历；
- 缺少项目经历；
- 缺少教育经历；
- 缺少可验证的量化成果；
- 岗位要求与已确认技能之间存在差集。

应用建议：

```http
POST /api/candidates/{candidateId}/resume-workspace/optimizations/{taskId}/apply
```

只有用户明确提交的建议 ID 才会应用。当前实现只允许业务代码白名单中的字段替换，应用成功后创建 `AI_OPTIMIZATION` 主版本。

应用前还会比较任务的 `source_version_id` 与当前主版本；如果用户在生成建议后又更新了简历，旧建议会被拒绝，必须基于最新版本重新生成，避免把过期建议覆盖到新内容上。

### 3.5 一键生成岗位定制简历

```http
POST /api/candidates/{candidateId}/resume-workspace/generate
```

请求：

```json
{
  "targetJobId": 1,
  "templateCode": "ATS_STANDARD_V1"
}
```

系统读取：

- 当前发布主简历；
- 已发布岗位标题；
- 岗位技能要求；
- 候选人已经确认的技能。

系统只突出岗位要求和候选技能的交集，不把缺失技能写入简历。生成结果是 `TARGETED` 版本，带 `target_job_id`，不会修改主简历当前指针。

导出：

```http
GET /api/candidates/{candidateId}/resume-workspace/versions/{versionId}/export
```

当前输出 UTF-8 ATS 单栏 HTML，内容经过 HTML 转义，浏览器打开后可以打印或另存为 PDF。这样避免由模型直接生成不稳定或包含脚本的 HTML。

### 3.6 根据简历生成招呼语

```http
POST /api/candidates/{candidateId}/resume-workspace/greetings
```

输入：

```json
{
  "jobId": 1,
  "tone": "PROFESSIONAL",
  "maximumCharacters": 120
}
```

系统先用代码计算简历技能与岗位技能交集，再生成简洁型、专业型、主动型和可选的 AI 个性化版本。输出包含：

- 招呼语内容；
- 字符数；
- 目标岗位；
- 工作年限来源说明；
- 匹配技能来源说明；
- 不自动群发的提示。

模型输入不包含手机号和邮箱，模型输出再次进行联系方式掩码。前端只提供复制按钮，不提供自动发送或批量发送。

## 4. 数据模型

### 4.1 `candidate_resume_workspace`

一名候选人一条工作台记录：

| 字段 | 用途 |
|---|---|
| `candidate_id` | 候选人唯一关联，数据库唯一 |
| `resume_name` | 工作台展示名称 |
| `current_version_id` | 当前发布的主版本 |
| `status` | 当前为 `ACTIVE` |

### 4.2 `candidate_resume_version`

| 字段 | 用途 |
|---|---|
| `version_no` | 工作台内单调递增版本号 |
| `version_type` | `MASTER` 或 `TARGETED` |
| `source_type` | 初始化、手动、PDF、DOCX、文本、AI 优化、一键生成 |
| `source_resume_id` | 原始 `talent_resume` 解析记录 |
| `target_job_id` | 岗位定制版本对应岗位 |
| `content_json` | 完整 `ResumeContent` 快照 |
| `quality_score` | 确定性综合质量分 |
| `change_summary` | 人可读的变更来源 |

### 4.3 `resume_optimization_task`

保存优化输入版本、目标岗位、优化前后预估分数、建议 JSON、任务状态和应用时间。

### 4.4 `resume_greeting_generation`

保存候选人、简历版本、岗位、语气、生成内容和事实依据，便于后续做复制率、编辑率和无证据内容审计。

### 4.5 `ResumeContent`

当前稳定字段：

```text
name
phone
email
city
desiredPosition
yearsOfExperience
summary
skills[]
workExperience
projectExperience
education
certificates
sourceText
```

`sourceText` 用作事实证据和质量检查，不进入导出的展示区域。

## 5. 质量评分

评分由 `ResumeQualityEvaluator` 确定性计算，模型不能修改分数。

| 维度 | 综合分权重 | 主要依据 |
|---|---:|---|
| 完整度 | 40% | 基本信息、概述、技能、工作、项目、教育 |
| 事实证据度 | 25% | 工作、项目和量化结果证据 |
| 表达清晰度 | 20% | 概述长度、技能、原始内容长度 |
| ATS 可读性 | 15% | 邮箱、工作、教育等标准字段 |

当前分数用于提示候选人完善内容，不用于招聘淘汰、人才排序或自动决策。

## 6. 权限和安全设计

### 6.1 对象级访问控制

`CandidateResumeAccessGuard` 从 JWT 请求属性读取 `userId` 和 `role`：

- `ADMIN`、`HR` 可以按业务权限访问候选人简历；
- 普通候选人必须满足 `talent_candidate.user_id = 当前登录用户 ID`；
- 不满足时返回“无权访问该候选人的简历”。

这防止了只修改 URL 中 `candidateId` 就读取他人简历的 IDOR 问题。

### 6.2 文件安全

当前已实现：

- 扩展名白名单；
- 20MB 服务端硬限制；
- Tika MIME 检测；
- 扩展名与内容类型一致性检查；
- 文件名不直接作为磁盘路径；
- 随机归档文件名；
- 归档路径归一化和目录逃逸检查；
- 候选人范围内 SHA-256 去重；
- 上传目录不进入 Git。

### 6.3 模型边界

- 手机号、邮箱在发给优化模型前被替换为占位符；
- 招呼语模型不接收联系方式；
- Prompt 明确说明输入是数据而不是指令；
- 模型输出只进入候选文案，不直接写数据库当前版本；
- 业务代码只应用字段白名单中的操作；
- 缺少模型配置时自动使用规则结果；
- 不记录任何模型 API Key 到业务表。

### 6.4 HTML 导出

候选人字段全部经过 `escapeHtml`，防止姓名、经历等内容被解释成脚本或标签。响应显式设置 `text/html;charset=UTF-8`，保证中文内容正确显示。

## 7. 代码地图

### 后端新增

```text
resume/ResumeContent.java
    稳定在线简历模型和导入合并规则

resume/ResumeQualityEvaluator.java
    确定性多维质量评分

resume/CandidateResumeAccessGuard.java
    候选人对象级权限校验

resume/ResumeWorkspaceService.java
    工作台、主版本、导入版本、发布、岗位版本和导出

resume/ResumeCopilotService.java
    字段级优化、建议应用和招呼语

resume/ResumeWorkspaceController.java
    新工作台 REST API
```

### 后端修改

```text
resume/ResumeService.java
    原始解析完成后同步创建工作台版本；重复检测改为候选人范围

resume/ResumeController.java
    原有上传、文本解析和详情接口增加候选人访问校验

resume/ResumeFileExtractor.java
    增加 20MB 限制和 MIME 一致性检查

resources/schema.sql
    增加 H2 开发环境工作台表

sql/init-mysql.sql
    增加 MySQL 新环境表和候选人范围文件唯一索引

sql/migrate-v4-resume-copilot.sql
    已有 MySQL 环境升级脚本
```

### 前端

```text
frontend/candidate/src/App.vue
    AI 简历工作台、在线编辑、导入预览、优化、岗位版本、招呼语和版本列表

frontend/candidate/src/workbench.css
    工作台独立样式

frontend/candidate/src/api.js
    支持 Blob 文件下载响应
```

### 测试

```text
ResumeWorkspaceIntegrationTest.java
    覆盖完整业务链路和跨候选人越权拦截
```

## 8. API 清单

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/api/candidates/{id}/resume-workspace` | 查询或初始化工作台 |
| PUT | `/api/candidates/{id}/resume-workspace` | 保存在线主简历新版本 |
| POST | `/api/candidates/{id}/resume-workspace/versions/{versionId}/publish` | 发布已预览导入版本 |
| POST | `/api/candidates/{id}/resume-workspace/optimize` | 生成字段级建议 |
| GET | `/api/candidates/{id}/resume-workspace/optimizations/{taskId}` | 查询优化任务 |
| POST | `/api/candidates/{id}/resume-workspace/optimizations/{taskId}/apply` | 应用用户选择的建议 |
| POST | `/api/candidates/{id}/resume-workspace/generate` | 创建岗位定制版本 |
| GET | `/api/candidates/{id}/resume-workspace/versions/{versionId}/export` | 下载可打印 HTML |
| POST | `/api/candidates/{id}/resume-workspace/greetings` | 生成证据化招呼语 |
| POST | `/api/resumes/upload` | 原有文件解析并写入工作台版本 |
| POST | `/api/resumes/parse-text` | 原有文本解析并写入工作台版本 |

## 9. 数据库升级

全新 MySQL 环境：

```powershell
mysql -u root -p < backend/sql/init-mysql.sql
```

已经执行过 v3 或更早脚本的环境：

```powershell
mysql -u root -p < backend/sql/migrate-v4-resume-copilot.sql
```

迁移脚本会把 `talent_resume.file_hash` 从全局唯一改成：

```text
candidate_id + file_hash
```

原因是不同候选人可能使用相同公开模板或上传相同学习样例，不应该互相阻塞；同一候选人仍不能重复导入完全相同的内容。

执行迁移前必须备份数据库，并先在测试环境验证。若旧数据库中唯一索引名称不是 MySQL 自动生成的 `file_hash`，需要先通过 `SHOW INDEX FROM talent_resume` 确认实际索引名，再相应调整迁移脚本中的 `DROP INDEX`。

## 10. 测试与验收

新增集成测试验证：

1. 初始化工作台和 V1；
2. 手动编辑生成 V2；
3. 文本导入生成待确认版本；
4. 明确发布导入版本；
5. 生成字段级优化任务；
6. 只应用选中建议；
7. 创建 `TARGETED` 岗位版本；
8. 导出 UTF-8 HTML；
9. 生成招呼语及事实依据；
10. 普通候选人不能读取另一候选人的工作台。

运行命令：

```powershell
cd backend
./mvnw.cmd test

cd ../frontend/candidate
npm.cmd run build
```

## 11. 当前边界与下一步

本次交付是可运行学习版，以下能力仍属于下一阶段，不应在说明中误报为已实现：

### 11.1 扫描 PDF OCR

当前 Tika 可以自动处理具有文本层的 PDF。纯图片扫描件仍会提示需要 OCR。生产版可以接入独立 OCR Worker，并增加页级坐标、OCR 置信度和人工校对。

### 11.2 字段级冲突选择

当前会生成“安全合并后的完整导入版本”，用户预览后整版发布。下一步可实现每个字段的 `KEEP_CURRENT`、`USE_IMPORTED`、`MANUAL_EDIT` 选择，以及字段来源页码和证据片段。

### 11.3 原生 PDF/DOCX 服务端导出

当前下载 ATS 单栏 HTML，候选人可以浏览器打印为 PDF。下一步可增加受控 DOCX 模板和服务端 PDF 渲染，但仍应由模板引擎渲染，不能让模型直接生成任意 HTML。

### 11.4 真正的异步导入

当前文本型文件在请求内解析。大规模生产系统应增加 `resume_import_task`、消息队列、OCR Worker、状态进度、幂等重试、失败队列和临时文件清理。

### 11.5 版本回滚与删除

数据模型已经支持把历史主版本重新发布，但当前前端只开放导入版本确认。下一步应增加通用历史版本对比、回滚、软删除和数据保留策略。

### 11.6 招呼语平台发送

当前只生成和复制，不自动发送。只有在目标平台提供正式接口、用户明确授权并完成限频、审计和撤销后，才考虑发送能力；不应使用爬虫或模拟点击规避平台规则。

## 12. 生产验收清单

在接入真实候选人数据前至少完成：

- [ ] 数据库从明文演示密码切换到正式身份系统；
- [ ] 原始简历使用对象存储加密和短时签名 URL；
- [ ] 增加恶意文件扫描和 PDF 主动内容清理；
- [ ] 增加 OCR 隔离 Worker；
- [ ] 为模型供应商完成个人信息处理评估和授权告知；
- [ ] 完成简历删除、导出和撤销授权接口；
- [ ] 增加版本保留期限和归档策略；
- [ ] 增加字段级证据和冲突决策；
- [ ] 增加请求限流、任务并发和模型预算；
- [ ] 增加操作审计和异常告警；
- [ ] 通过真实简历的解析准确率评估；
- [ ] 把 AI 无证据事实率作为阻断上线指标；
- [ ] 对所有导出模板进行中文、英文、长文本和分页测试；
- [ ] 由安全和法务审核隐私政策与用户授权流程。

## 13. 最终设计结论

本次实现的关键不是多加几个模型按钮，而是建立了正确的业务边界：

1. 在线主简历保存事实；
2. 文件导入产生待确认版本；
3. AI 优化产生字段级建议；
4. 用户确认后才产生新主版本；
5. 岗位定制版本不污染主简历；
6. 招呼语引用简历与岗位证据；
7. 所有文件、版本、优化和沟通都可以继续审计和扩展。

这套结构可以继续承载 OCR、字段级合并、DOCX/PDF 模板、投递版本绑定、求职信、自我介绍、STAR 故事库和多渠道沟通，而不需要推翻当前核心模型。
