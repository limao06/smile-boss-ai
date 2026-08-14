# SmileAgent 多 Agent 协作平台 MVP

## 1. 已实现范围

当前版本已经在 SmileBoss Spring Boot 后端中实现一个可运行的多 Agent 工作流内核：

- Agent 定义、版本和发布状态；
- 工作流定义、版本、节点、边和发布状态；
- `START`、`AGENT`、`RULE`、`ROUTER`、`JOIN`、`HUMAN_APPROVAL`、`END` 节点；
- DAG 合法性检查；
- 分支扇出与 JOIN 汇总；
- Agent JSON 输出与必填字段检查；
- 模型未启用时的离线安全输出，便于本地演示完整运行链；
- 节点级任务、幂等键、有限重试、预算和超时；
- Workflow Event、Artifact、Message、Checkpoint；
- 人工审批、暂停和恢复；
- 管理员权限限制；
- 候选人综合报告内置流程；
- H2 与 MySQL 初始化脚本；
- MockMvc 集成测试。
- 招聘管理端 Agent 控制台：Agent 注册表、工作流、运行记录、Artifact/Event 轨迹和人工审批。

MVP 的并行表示为逻辑 fan-out：多个分支创建独立任务，全部完成后进入 JOIN。当前单实例执行器按队列消费任务；后续接入 Redis Stream/消费者组后，同一工作流的不同分支可以由多个 Worker 物理并发执行，数据库模型和 API 不需要改变。

## 2. 代码入口

- `backend/smile-app/src/main/java/com/smileboss/agent/AgentPlatformController.java`
- `backend/smile-app/src/main/java/com/smileboss/agent/AgentPlatformService.java`
- `backend/smile-app/src/main/java/com/smileboss/agent/WorkflowEngineService.java`
- `backend/smile-app/src/test/java/com/smileboss/AgentPlatformIntegrationTest.java`
- `backend/smile-app/src/main/resources/schema.sql`
- `backend/smile-app/src/main/resources/data.sql`
- `backend/sql/init-mysql.sql`

## 3. 内置候选报告流程

```text
START
├── RESUME_RESEARCH       简历证据研究 Agent
└── JOB_FIT_RESEARCH      岗位适配研究 Agent
          ↓
EVIDENCE_JOIN             汇总两个分支 Artifact
          ↓
REPORT_WRITE              候选报告写作 Agent
          ↓
POLICY_AUDIT              政策与偏差审计 Agent
          ↓
HUMAN_REVIEW              等待 HR 人工审核
          ↓
END                       生成最终 Artifact lineage
```

内置 Agent：

| Agent 编码 | 职责 |
|---|---|
| `RESUME_RESEARCHER` | 只从候选资料抽取事实、证据和未知项 |
| `JOB_FIT_RESEARCHER` | 将岗位要求与候选证据逐项对齐 |
| `CANDIDATE_REPORT_WRITER` | 只基于上游 Artifact 写报告 |
| `POLICY_AUDITOR` | 检查敏感属性、无证据结论和自动决策风险 |

## 4. 本地启动

默认 `dev` Profile 使用 H2，模型默认关闭，但仍可运行完整工作流和人工审批链路：

```powershell
cd <项目目录>\backend
.\mvnw.cmd -pl smile-app -am spring-boot:run
```

需要生成真实 Agent 内容时，配置现有模型环境变量并启用：

```powershell
$env:LLM_ENABLED = 'true'
$env:CLAUDE_API_KEY = '你的密钥'
.\mvnw.cmd -pl smile-app -am spring-boot:run
```

MySQL 环境首次运行前执行更新后的：

```text
backend/sql/init-mysql.sql
```

管理端启动后，使用 `admin/admin123` 登录，在左侧进入“Agent 协作平台”：

```powershell
cd <项目目录>\frontend\admin
npm.cmd run dev
```

管理端可以直接启动内置候选报告工作流、查看运行轨迹、展开每个 Artifact，并处理批准或拒绝。

## 5. API 使用示例

### 5.1 登录

```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "admin123"
}
```

后续请求添加：

```http
Authorization: Bearer <token>
```

### 5.2 查看 Agent 和工作流

```http
GET /api/agent-platform/agents
GET /api/agent-platform/agents/RESUME_RESEARCHER
GET /api/agent-platform/workflows
GET /api/agent-platform/workflows/CANDIDATE_REPORT
```

### 5.3 启动候选人综合报告

```http
POST /api/agent-platform/runs
Content-Type: application/json

{
  "workflowCode": "CANDIDATE_REPORT",
  "businessType": "CANDIDATE_JOB",
  "businessId": "1:1",
  "input": {
    "candidateId": 1,
    "jobId": 1,
    "goal": "生成候选人与 Java 高级开发岗位的证据化综合报告"
  }
}
```

返回状态应为：

```text
WAITING_HUMAN
```

响应同时包含：

- `tasks`：每个节点任务；
- `artifacts`：每个 Agent/工作流节点交付物；
- `events`：完整运行事件；
- `humanApprovals`：待处理人工任务；
- `state_json`：当前版本化状态；
- `trace_id`：本次运行追踪号。

### 5.4 人工审批

先查询：

```http
GET /api/agent-platform/human-tasks
```

审批：

```http
POST /api/agent-platform/human-tasks/{taskId}/decisions
Content-Type: application/json

{
  "decision": "APPROVE",
  "comment": "证据链和合规审计结果已人工核对"
}
```

也可使用：

```json
{
  "decision": "REJECT",
  "comment": "报告证据不足，不予发布"
}
```

两个决定都会形成不可变 `HUMAN_DECISION` Artifact。工作流根据条件边进入 END；最终 Artifact 保留全部产物 lineage 和人工决定。

### 5.5 查询和取消运行

```http
GET  /api/agent-platform/runs
GET  /api/agent-platform/runs/{runId}
POST /api/agent-platform/runs/{runId}/cancel
```

## 6. 创建自定义 Agent

```http
POST /api/agent-platform/agents
Content-Type: application/json

{
  "code": "JOB_DESCRIPTION_REVIEWER",
  "name": "岗位文案审查 Agent",
  "description": "检查岗位描述的完整性和不当限制",
  "systemPrompt": "只输出 JSON，检查岗位描述的职责、要求、敏感限制和未知项。",
  "modelPolicy": "STRUCTURED_EXTRACTION",
  "inputSchema": {
    "type": "object",
    "required": ["jobId"]
  },
  "outputSchema": {
    "type": "object",
    "required": ["passed", "issues", "suggestions"]
  },
  "toolPolicy": {
    "mode": "NO_SIDE_EFFECT"
  },
  "knowledgeScopes": ["JOB_REQUIREMENT", "RECRUITMENT_POLICY"],
  "maxModelCalls": 2,
  "timeoutSeconds": 120
}
```

创建后为 `DRAFT`，管理员发布：

```http
POST /api/agent-platform/agents/JOB_DESCRIPTION_REVIEWER/versions/1/publish
```

## 7. 创建自定义工作流

```http
POST /api/agent-platform/workflows
Content-Type: application/json

{
  "code": "JOB_DESCRIPTION_AUDIT",
  "name": "岗位文案审核工作流",
  "inputSchema": {
    "type": "object",
    "required": ["jobId"]
  },
  "budget": {
    "maxNodeExecutions": 10,
    "maxModelCalls": 5,
    "maxDurationSeconds": 300
  },
  "nodes": [
    {"code": "START", "type": "START", "name": "开始"},
    {
      "code": "REVIEW",
      "type": "AGENT",
      "name": "文案审查",
      "config": {
        "agentCode": "JOB_DESCRIPTION_REVIEWER",
        "artifactType": "JOB_DESCRIPTION_REVIEW"
      },
      "retry": {"maxAttempts": 2}
    },
    {
      "code": "HUMAN_REVIEW",
      "type": "HUMAN_APPROVAL",
      "name": "人工确认",
      "config": {
        "title": "请确认岗位文案审查结果",
        "instruction": "核对问题和修改建议"
      }
    },
    {"code": "END", "type": "END", "name": "结束"}
  ],
  "edges": [
    {"from": "START", "to": "REVIEW"},
    {"from": "REVIEW", "to": "HUMAN_REVIEW"},
    {"from": "HUMAN_REVIEW", "to": "END", "condition": "decision==APPROVE"},
    {"from": "HUMAN_REVIEW", "to": "END", "condition": "decision==REJECT"}
  ]
}
```

发布：

```http
POST /api/agent-platform/workflows/JOB_DESCRIPTION_AUDIT/versions/1/publish
```

发布时会检查所有 AGENT 节点引用的 Agent 是否已经发布。

## 8. 安全与一致性

- 管理接口当前只允许 `ADMIN` 角色使用；
- 候选上下文不会向 Agent 提供电话和邮箱；
- 外部文本在 system prompt 中明确标记为不可信数据；
- Agent 只输出 Artifact，不能直接修改招聘业务状态；
- 每个任务有唯一幂等键；
- 工作流定义必须是无环 DAG；
- 节点重试上限在运行时强制限制为 1–5；
- 工作流限制节点执行数、逻辑模型调用数和总时间；
- 人工审批形成独立、可审计 Artifact；
- MySQL 是状态真相，Redis/Qdrant 后续只作为队列、缓存和检索派生层。

## 9. 当前 MVP 边界

- fan-out 已有独立任务和 JOIN 语义，但单实例中仍按队列依次执行；
- Agent 输出执行必填字段校验，尚未实现完整 JSON Schema Draft 校验器；
- 逻辑模型调用预算按成功 AGENT 节点计数；供应商内部 fallback 次数仍记录在现有 `ai_model_call_log`；
- `RULE`、`ROUTER` 当前提供基础合并和 `input.key==value`/`decision==value` 条件；复杂表达式引擎尚未开放；
- 工具调用审计表已经建立，但 Tool Gateway 将在下一阶段接入；
- 工作流 MVP 暂不允许环，审核返工推荐通过显式子工作流实现；
- Qdrant 知识检索尚未接到此运行内核，后续通过 `RAG_RETRIEVAL`/Tool 节点接入现有向量配置。

## 10. 测试

```powershell
cd <项目目录>\backend
.\mvnw.cmd -pl smile-app -am test
```

集成测试覆盖：

- 内置 Agent 和工作流查询；
- 候选报告 fan-out/JOIN；
- 模型关闭时离线 Artifact；
- 暂停等待人工；
- 人工批准后恢复并完成；
- Event 和最终 Artifact；
- 创建、发布自定义 Agent；
- 创建、发布和运行自定义工作流；
- 既有招聘系统冒烟测试。
