# SmileBoss AI 核心 API

统一入口：`http://localhost:8080`。除登录和健康检查外，请求需携带 `Authorization: Bearer <token>`。

| 能力 | 方法与地址 |
|---|---|
| 登录 | `POST /api/auth/login` |
| 职位列表/创建 | `GET/POST /api/jobs` |
| 候选人列表/创建 | `GET/POST /api/candidates` |
| 上传并解析简历 | `POST /api/resumes/upload?candidateId=1` |
| 解析粘贴文本 | `POST /api/resumes/parse-text` |
| 岗位推荐 | `POST /api/ai/candidates/{candidateId}/recommend-jobs` |
| 开始模拟面试 | `POST /api/mock-interviews` |
| 回答模拟面试 | `POST /api/mock-interviews/{sessionId}/answers` |
| 创建正式面试邀请 | `POST /api/ai-interviews/invitations` |
| 正式面试授权并开始 | `POST /api/ai-interviews/invitations/{id}/start` |
| 回答正式面试 | `POST /api/ai-interviews/sessions/{id}/answers` |
| 正式面试报告 | `GET /api/ai-interviews/sessions/{id}/report` |
| HR 审核 | `POST /api/ai-interviews/sessions/{id}/review` |
| 记录行为事件 | `POST /api/activity/events` |
| 活跃度分析 | `GET /api/activity/candidates/{id}/analysis` |
| 智能问数生成/执行 | `POST /api/text-to-sql/queries` |
| 智能问数运行列表 | `GET /api/text-to-sql/queries` |
| 智能问数运行详情 | `GET /api/text-to-sql/queries/{runId}` |
| 敏感查询人工决定 | `POST /api/text-to-sql/queries/{runId}/decisions` |
| 智能问数反馈 | `POST /api/text-to-sql/queries/{runId}/feedback` |
| 统一待审核列表 | `GET /api/agent-platform/human-tasks` |
| 认领审核任务 | `POST /api/agent-platform/human-tasks/{approvalId}/claim` |

正式 AI 面试允许的 HR 审核结论：`NEXT_ROUND`、`SUPPLEMENT`、`TALENT_POOL`、`REJECT`。AI 不会自动提交其中任何一个结论。

智能问数请求示例：

```json
{
  "question": "每个职位的推荐平均分是多少？",
  "execute": true,
  "maxRows": 200
}
```

`execute=false` 时只生成和校验，不读取结果数据。`execute=true` 时，L0/L1 查询自动执行；L2 敏感查询返回 `WAITING_HUMAN`，由审核接口提交 `APPROVE` 或 `REJECT`。L3 表示 SQL 安全校验失败，不允许通过人工审核绕过。
