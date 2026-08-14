MERGE INTO sys_user (id, username, password, display_name, role, status) KEY(id) VALUES
 (1, 'admin', 'admin123', '招聘管理员', 'ADMIN', 1),
 (2, 'candidate', 'demo123', '演示候选人', 'CANDIDATE', 1);

MERGE INTO recruit_job (id, title, department, city, salary_min, salary_max, experience_years, required_skills, description, status) KEY(id) VALUES
 (1, 'Java 高级开发工程师', '研发中心', '上海', 25000, 35000, 5, 'Java,Spring Boot,MySQL,Redis,微服务', '负责交易平台核心服务设计与开发，要求具备高并发系统经验。', 'PUBLISHED'),
 (2, 'AI 产品经理', '智能产品部', '杭州', 20000, 30000, 3, '产品设计,大模型,RAG,数据分析', '负责智能招聘产品规划、用户研究与落地。', 'PUBLISHED');

MERGE INTO talent_candidate (id, user_id, name, phone, email, city, years_of_experience, desired_position, skills, profile_summary) KEY(id) VALUES
 (1, 2, '演示候选人', '13800000000', 'demo@example.com', '上海', 5, 'Java 高级开发工程师', 'Java,Spring Boot,MySQL,Redis', '5 年 Java 后端经验，参与订单和交易系统开发。');

MERGE INTO ai_agent_definition (id, agent_code, name, description, status, current_version, created_by) KEY(id) VALUES
 (101, 'RESUME_RESEARCHER', '简历证据研究 Agent', '从候选人结构化资料中提取与岗位相关、可引用的事实和未知项', 'PUBLISHED', 1, 1),
 (102, 'JOB_FIT_RESEARCHER', '岗位适配研究 Agent', '分析岗位能力要求，并将要求与候选证据逐项对齐', 'PUBLISHED', 1, 1),
 (103, 'CANDIDATE_REPORT_WRITER', '候选报告写作 Agent', '只基于上游证据撰写结构化候选报告，不新增无证据事实', 'PUBLISHED', 1, 1),
 (104, 'POLICY_AUDITOR', '合规审计 Agent', '检查敏感属性、越权内容、无证据结论和自动化决策风险', 'PUBLISHED', 1, 1);

MERGE INTO ai_agent_version (id, agent_code, version, system_prompt, model_policy, input_schema, output_schema, tool_policy, knowledge_scopes, max_model_calls, timeout_seconds, status) KEY(id) VALUES
 (201, 'RESUME_RESEARCHER', 1, '你是简历证据研究 Agent。只依据输入中的候选资料提取事实，不得编造。输出 JSON 对象，包含 summary、claims、evidence、unknowns；不要使用性别、年龄、婚育、民族、宗教等敏感属性。', 'STRUCTURED_EXTRACTION', '{}', '{"type":"object","required":["summary","claims","evidence","unknowns"]}', 'NO_SIDE_EFFECT', '["RESUME_EVIDENCE"]', 2, 120, 'PUBLISHED'),
 (202, 'JOB_FIT_RESEARCHER', 1, '你是岗位适配研究 Agent。将岗位要求和候选证据逐项对齐。只输出 JSON 对象，包含 summary、matchedRequirements、gaps、questionsToVerify。缺少证据时标记未知，不得自动淘汰候选人。', 'HIGH_ACCURACY_REASONING', '{}', '{"type":"object","required":["summary","matchedRequirements","gaps","questionsToVerify"]}', 'NO_SIDE_EFFECT', '["JOB_REQUIREMENT","RESUME_EVIDENCE"]', 2, 120, 'PUBLISHED'),
 (203, 'CANDIDATE_REPORT_WRITER', 1, '你是候选报告写作 Agent。只能使用上游 Artifact 中的证据写作。输出 JSON 对象，包含 executiveSummary、strengths、gaps、unknowns、evidenceReferences、recommendationNotice。recommendationNotice 必须说明 AI 仅供辅助并需要人工审核。', 'LONG_CONTEXT_WRITING', '{}', '{"type":"object","required":["executiveSummary","strengths","gaps","unknowns","evidenceReferences","recommendationNotice"]}', 'NO_SIDE_EFFECT', '["RESUME_EVIDENCE","JOB_REQUIREMENT"]', 2, 120, 'PUBLISHED'),
 (204, 'POLICY_AUDITOR', 1, '你是独立合规审计 Agent。检查上游候选报告中的敏感属性、无证据结论、绝对化语言、越权内容和自动淘汰倾向。输出 JSON 对象，包含 passed、issues、requiredActions、notice。', 'INDEPENDENT_AUDIT', '{}', '{"type":"object","required":["passed","issues","requiredActions","notice"]}', 'NO_SIDE_EFFECT', '["RECRUITMENT_POLICY"]', 2, 120, 'PUBLISHED');

MERGE INTO ai_workflow_definition (id, workflow_code, name, description, status, current_version, created_by) KEY(id) VALUES
 (301, 'CANDIDATE_REPORT', '候选人综合报告工作流', '简历证据和岗位要求并行研究，汇总后写作、合规审计并等待 HR 人工审批', 'PUBLISHED', 1, 1);

MERGE INTO ai_workflow_version (id, workflow_code, version, state_schema, input_schema, output_schema, budget_json, status) KEY(id) VALUES
 (401, 'CANDIDATE_REPORT', 1, '{}', '{"type":"object","required":["candidateId","jobId"]}', '{"type":"object"}', '{"maxNodeExecutions":30,"maxModelCalls":12,"maxDurationSeconds":900}', 'PUBLISHED');

MERGE INTO ai_workflow_node (id, workflow_code, workflow_version, node_code, node_type, name, config_json, retry_json, timeout_seconds) KEY(id) VALUES
 (501, 'CANDIDATE_REPORT', 1, 'START', 'START', '开始', '{"artifactType":"WORKFLOW_INPUT"}', '{"maxAttempts":1}', 30),
 (502, 'CANDIDATE_REPORT', 1, 'RESUME_RESEARCH', 'AGENT', '简历证据研究', '{"agentCode":"RESUME_RESEARCHER","artifactType":"RESUME_RESEARCH_NOTES"}', '{"maxAttempts":2}', 120),
 (503, 'CANDIDATE_REPORT', 1, 'JOB_FIT_RESEARCH', 'AGENT', '岗位适配研究', '{"agentCode":"JOB_FIT_RESEARCHER","artifactType":"JOB_FIT_RESEARCH_NOTES"}', '{"maxAttempts":2}', 120),
 (504, 'CANDIDATE_REPORT', 1, 'EVIDENCE_JOIN', 'JOIN', '证据汇总', '{"artifactType":"CANDIDATE_EVIDENCE_BUNDLE"}', '{"maxAttempts":1}', 30),
 (505, 'CANDIDATE_REPORT', 1, 'REPORT_WRITE', 'AGENT', '报告写作', '{"agentCode":"CANDIDATE_REPORT_WRITER","artifactType":"CANDIDATE_REPORT_DRAFT"}', '{"maxAttempts":2}', 120),
 (506, 'CANDIDATE_REPORT', 1, 'POLICY_AUDIT', 'AGENT', '政策与偏差审计', '{"agentCode":"POLICY_AUDITOR","artifactType":"POLICY_AUDIT_RESULT"}', '{"maxAttempts":2}', 120),
 (507, 'CANDIDATE_REPORT', 1, 'HUMAN_REVIEW', 'HUMAN_APPROVAL', 'HR 人工审核', '{"title":"请审核候选人综合报告","instruction":"核对证据、未知项与合规审计结果后决定是否发布"}', '{"maxAttempts":1}', 86400),
 (508, 'CANDIDATE_REPORT', 1, 'END', 'END', '结束', '{"artifactType":"CANDIDATE_REPORT_FINAL"}', '{"maxAttempts":1}', 30);

MERGE INTO ai_workflow_edge (id, workflow_code, workflow_version, from_node, to_node, condition_expression, priority) KEY(id) VALUES
 (601, 'CANDIDATE_REPORT', 1, 'START', 'RESUME_RESEARCH', 'ALWAYS', 10),
 (602, 'CANDIDATE_REPORT', 1, 'START', 'JOB_FIT_RESEARCH', 'ALWAYS', 20),
 (603, 'CANDIDATE_REPORT', 1, 'RESUME_RESEARCH', 'EVIDENCE_JOIN', 'ALWAYS', 10),
 (604, 'CANDIDATE_REPORT', 1, 'JOB_FIT_RESEARCH', 'EVIDENCE_JOIN', 'ALWAYS', 20),
 (605, 'CANDIDATE_REPORT', 1, 'EVIDENCE_JOIN', 'REPORT_WRITE', 'ALWAYS', 10),
 (606, 'CANDIDATE_REPORT', 1, 'REPORT_WRITE', 'POLICY_AUDIT', 'ALWAYS', 10),
 (607, 'CANDIDATE_REPORT', 1, 'POLICY_AUDIT', 'HUMAN_REVIEW', 'ALWAYS', 10),
 (608, 'CANDIDATE_REPORT', 1, 'HUMAN_REVIEW', 'END', 'decision==APPROVE', 10),
 (609, 'CANDIDATE_REPORT', 1, 'HUMAN_REVIEW', 'END', 'decision==REJECT', 20);

MERGE INTO ai_approval_policy (id, policy_code, version, name, approval_type, enabled, priority, condition_json, reviewer_group, required_approvals, sla_minutes, timeout_action, created_by) KEY(id) VALUES
 (701, 'WORKFLOW_DEFAULT_REVIEW', 1, '工作流默认人工审核', 'WORKFLOW', 1, 100, '{"riskLevels":["L1","L2","L3"]}', 'HR_ADMIN', 1, 1440, 'ESCALATE', 1),
 (702, 'TEXT_TO_SQL_SENSITIVE_REVIEW', 1, '智能问数敏感数据审核', 'TEXT_TO_SQL', 1, 10, '{"riskLevels":["L2","L3"]}', 'DATA_REVIEWER', 1, 240, 'REJECT', 1);

MERGE INTO ai_data_source (id, source_code, name, dialect, connection_ref, read_only_flag, status) KEY(id) VALUES
 (801, 'RECRUITMENT_MAIN', '招聘业务主库只读视图', 'MYSQL', 'spring.datasource', 1, 'ACTIVE');

MERGE INTO ai_semantic_model (id, model_code, version, name, domain_code, source_code, catalog_json, status) KEY(id) VALUES
 (901, 'RECRUITMENT_ANALYTICS', 1, '招聘分析语义模型', 'RECRUITMENT', 'RECRUITMENT_MAIN', '{"tables":["recruit_job","talent_candidate","talent_resume","recruit_recommendation","ai_interview_invitation","ai_interview_session","candidate_activity_event"],"relationships":["recruit_recommendation.job_id=recruit_job.id","recruit_recommendation.candidate_id=talent_candidate.id","ai_interview_session.job_id=recruit_job.id","ai_interview_session.candidate_id=talent_candidate.id"],"rules":["只允许SELECT/CTE","默认最多200行","联系方式和简历原文属于敏感字段"]}', 'PUBLISHED');

MERGE INTO ai_metric_definition (id, metric_code, version, name, description, expression_sql, dimensions_json, filters_json, sensitivity_level, status) KEY(id) VALUES
 (1001, 'PUBLISHED_JOB_COUNT', 1, '在招职位数', '状态为PUBLISHED的职位数量', 'COUNT(*)', '["department","city"]', '{"status":"PUBLISHED"}', 'L0', 'PUBLISHED'),
 (1002, 'CANDIDATE_COUNT', 1, '候选人数', '人才库候选人总量', 'COUNT(*)', '["city","desired_position"]', '{}', 'L0', 'PUBLISHED'),
 (1003, 'RECOMMENDATION_AVG_SCORE', 1, '推荐平均分', '按职位统计AI岗位推荐平均分', 'AVG(score)', '["job_id"]', '{}', 'L1', 'PUBLISHED');

INSERT INTO ai_sql_example(domain_code, question, sql_text, tags_json, quality_score, status)
SELECT 'RECRUITMENT', '每个城市有多少候选人', 'SELECT city, COUNT(*) AS candidate_count FROM talent_candidate GROUP BY city ORDER BY candidate_count DESC LIMIT 200', '["候选人","城市","聚合"]', 95, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM ai_sql_example WHERE question='每个城市有多少候选人');
