CREATE DATABASE IF NOT EXISTS smile_boss_ai DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE smile_boss_ai;

CREATE TABLE IF NOT EXISTS sys_user (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, username VARCHAR(64) NOT NULL UNIQUE, password VARCHAR(128) NOT NULL,
 display_name VARCHAR(64) NOT NULL, role VARCHAR(32) NOT NULL, status INT NOT NULL DEFAULT 1,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS recruit_job (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, title VARCHAR(128) NOT NULL, department VARCHAR(128), city VARCHAR(64),
 salary_min INT, salary_max INT, experience_years INT DEFAULT 0, required_skills VARCHAR(1000), description TEXT,
 status VARCHAR(32) NOT NULL DEFAULT 'DRAFT', created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS talent_candidate (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT, name VARCHAR(64) NOT NULL, phone VARCHAR(32), email VARCHAR(128),
 city VARCHAR(64), years_of_experience INT DEFAULT 0, desired_position VARCHAR(128), skills VARCHAR(1000), profile_summary TEXT,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 INDEX idx_candidate_phone(phone), INDEX idx_candidate_email(email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS talent_resume (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, candidate_id BIGINT NOT NULL, file_name VARCHAR(255), file_hash VARCHAR(64) UNIQUE,
 raw_text LONGTEXT, structured_json JSON, parse_status VARCHAR(32) NOT NULL, completeness_score INT DEFAULT 0,
 completeness_json JSON, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, INDEX idx_resume_candidate(candidate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS recruit_recommendation (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, candidate_id BIGINT NOT NULL, job_id BIGINT NOT NULL, score INT NOT NULL,
 result_json JSON, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY uk_recommend(candidate_id,job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS mock_interview_session (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, candidate_id BIGINT NOT NULL, job_id BIGINT, interview_type VARCHAR(32) NOT NULL,
 status VARCHAR(32) NOT NULL, current_round INT DEFAULT 0, score INT, report_json JSON,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, finished_at TIMESTAMP NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS mock_interview_turn (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, session_id BIGINT NOT NULL, round_no INT NOT NULL, question TEXT NOT NULL,
 answer TEXT, evaluation_json JSON, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, INDEX idx_mock_turn_session(session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_interview_invitation (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, candidate_id BIGINT NOT NULL, job_id BIGINT NOT NULL, title VARCHAR(255) NOT NULL,
 duration_minutes INT NOT NULL DEFAULT 25, expires_at TIMESTAMP NULL, status VARCHAR(32) NOT NULL,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_interview_session (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, invitation_id BIGINT NOT NULL, candidate_id BIGINT NOT NULL, job_id BIGINT NOT NULL,
 status VARCHAR(32) NOT NULL, current_stage VARCHAR(32) NOT NULL, current_round INT DEFAULT 0, plan_json JSON,
 report_json JSON, ai_score INT, hr_decision VARCHAR(32), hr_comment TEXT, started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 finished_at TIMESTAMP NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_interview_turn (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, session_id BIGINT NOT NULL, round_no INT NOT NULL, competency VARCHAR(64),
 question TEXT NOT NULL, answer TEXT, score INT, evidence TEXT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_ai_turn_session(session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS candidate_activity_event (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, candidate_id BIGINT NOT NULL, event_type VARCHAR(64) NOT NULL,
 object_type VARCHAR(64), object_id BIGINT, metadata_json JSON, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_activity_candidate_time(candidate_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_model_call_log (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, provider VARCHAR(32), model VARCHAR(128), scene VARCHAR(64), latency_ms BIGINT,
 status VARCHAR(32), error_message TEXT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_model_log_time(created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_agent_definition (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, agent_code VARCHAR(96) NOT NULL UNIQUE, name VARCHAR(128) NOT NULL,
 description TEXT, status VARCHAR(32) NOT NULL DEFAULT 'DRAFT', current_version INT NOT NULL DEFAULT 1,
 created_by BIGINT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_agent_version (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, agent_code VARCHAR(96) NOT NULL, version INT NOT NULL, system_prompt LONGTEXT NOT NULL,
 model_policy VARCHAR(64) NOT NULL DEFAULT 'HIGH_ACCURACY_REASONING', input_schema JSON, output_schema JSON,
 tool_policy JSON, knowledge_scopes JSON, max_model_calls INT NOT NULL DEFAULT 2, timeout_seconds INT NOT NULL DEFAULT 120,
 status VARCHAR(32) NOT NULL DEFAULT 'DRAFT', created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_agent_version(agent_code,version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_workflow_definition (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, workflow_code VARCHAR(96) NOT NULL UNIQUE, name VARCHAR(128) NOT NULL,
 description TEXT, status VARCHAR(32) NOT NULL DEFAULT 'DRAFT', current_version INT NOT NULL DEFAULT 1,
 created_by BIGINT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_workflow_version (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, workflow_code VARCHAR(96) NOT NULL, version INT NOT NULL,
 state_schema JSON, input_schema JSON, output_schema JSON, budget_json JSON, status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY uk_workflow_version(workflow_code,version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_workflow_node (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, workflow_code VARCHAR(96) NOT NULL, workflow_version INT NOT NULL,
 node_code VARCHAR(96) NOT NULL, node_type VARCHAR(32) NOT NULL, name VARCHAR(128) NOT NULL,
 config_json JSON, retry_json JSON, timeout_seconds INT NOT NULL DEFAULT 120, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_workflow_node(workflow_code,workflow_version,node_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_workflow_edge (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, workflow_code VARCHAR(96) NOT NULL, workflow_version INT NOT NULL,
 from_node VARCHAR(96) NOT NULL, to_node VARCHAR(96) NOT NULL, condition_expression VARCHAR(512) NOT NULL DEFAULT 'ALWAYS',
 priority INT NOT NULL DEFAULT 0, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_workflow_edge_from(workflow_code,workflow_version,from_node)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_workflow_run (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL DEFAULT 1, workflow_code VARCHAR(96) NOT NULL,
 workflow_version INT NOT NULL, business_type VARCHAR(64), business_id VARCHAR(128), status VARCHAR(32) NOT NULL,
 state_json JSON NOT NULL, state_version INT NOT NULL DEFAULT 0, budget_json JSON, used_budget_json JSON,
 trace_id VARCHAR(64) NOT NULL, created_by BIGINT NOT NULL, started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 deadline_at TIMESTAMP NULL, completed_at TIMESTAMP NULL,
 INDEX idx_workflow_run_status(status), INDEX idx_workflow_run_creator(created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_workflow_task (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, run_id BIGINT NOT NULL, node_code VARCHAR(96) NOT NULL, node_type VARCHAR(32) NOT NULL,
 agent_code VARCHAR(96), agent_version INT, status VARCHAR(32) NOT NULL, attempt_no INT NOT NULL DEFAULT 1,
 input_artifact_ids JSON, output_artifact_ids JSON, idempotency_key VARCHAR(192) NOT NULL UNIQUE,
 scheduled_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, started_at TIMESTAMP NULL, finished_at TIMESTAMP NULL,
 error_code VARCHAR(96), error_message TEXT, INDEX idx_workflow_task_run(run_id), INDEX idx_workflow_task_status(status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_artifact (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL DEFAULT 1, run_id BIGINT NOT NULL, task_id BIGINT,
 artifact_type VARCHAR(96) NOT NULL, schema_version VARCHAR(32) NOT NULL DEFAULT 'v1', content_json JSON NOT NULL,
 evidence_ids JSON, confidence DECIMAL(6,5), status VARCHAR(32) NOT NULL DEFAULT 'CREATED', producer_type VARCHAR(32) NOT NULL,
 producer_code VARCHAR(96) NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_artifact_run(run_id), INDEX idx_artifact_task(task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_message (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, run_id BIGINT NOT NULL, task_id BIGINT, message_type VARCHAR(32) NOT NULL,
 sender VARCHAR(96) NOT NULL, receiver VARCHAR(96), artifact_id BIGINT, payload_json JSON,
 idempotency_key VARCHAR(192) NOT NULL UNIQUE, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_message_run(run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_workflow_event (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, run_id BIGINT NOT NULL, task_id BIGINT, event_type VARCHAR(64) NOT NULL,
 node_code VARCHAR(96), payload_json JSON, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_workflow_event_run(run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_workflow_checkpoint (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, run_id BIGINT NOT NULL, state_version INT NOT NULL, node_code VARCHAR(96),
 state_json JSON NOT NULL, artifact_ids JSON, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_checkpoint(run_id,state_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_human_approval (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, run_id BIGINT NOT NULL, task_id BIGINT NOT NULL UNIQUE, node_code VARCHAR(96) NOT NULL,
 approval_type VARCHAR(64) NOT NULL DEFAULT 'WORKFLOW', origin_type VARCHAR(64) NOT NULL DEFAULT 'WORKFLOW_RUN',
 origin_id BIGINT, request_token VARCHAR(64), policy_code VARCHAR(96), policy_version INT, risk_level VARCHAR(16) NOT NULL DEFAULT 'L1',
 reviewer_group VARCHAR(96) NOT NULL DEFAULT 'HR_ADMIN', assigned_to BIGINT, claimed_at TIMESTAMP NULL,
 lease_expires_at TIMESTAMP NULL, due_at TIMESTAMP NULL, timeout_action VARCHAR(32) NOT NULL DEFAULT 'ESCALATE',
 title VARCHAR(255) NOT NULL, instruction TEXT, request_payload_json JSON, decision_payload_json JSON,
 input_snapshot_hash VARCHAR(64), resume_checkpoint_id BIGINT, decision_version INT NOT NULL DEFAULT 0,
 status VARCHAR(32) NOT NULL DEFAULT 'PENDING', decision VARCHAR(32), comment TEXT,
 requested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, expires_at TIMESTAMP NULL, decided_by BIGINT, decided_at TIMESTAMP NULL,
 INDEX idx_human_approval_status(status), INDEX idx_human_approval_assignee(assigned_to,status),
 INDEX idx_human_approval_origin(origin_type,origin_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_approval_policy (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, policy_code VARCHAR(96) NOT NULL, version INT NOT NULL, name VARCHAR(128) NOT NULL,
 approval_type VARCHAR(64) NOT NULL, enabled INT NOT NULL DEFAULT 1, priority INT NOT NULL DEFAULT 100,
 condition_json JSON, reviewer_group VARCHAR(96) NOT NULL DEFAULT 'HR_ADMIN', required_approvals INT NOT NULL DEFAULT 1,
 sla_minutes INT NOT NULL DEFAULT 1440, timeout_action VARCHAR(32) NOT NULL DEFAULT 'ESCALATE', created_by BIGINT,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY uk_approval_policy(policy_code,version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_human_approval_action (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, approval_id BIGINT NOT NULL, action_type VARCHAR(32) NOT NULL, actor_id BIGINT,
 from_status VARCHAR(32), to_status VARCHAR(32), payload_json JSON, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_approval_action(approval_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_approval_notification_outbox (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, approval_id BIGINT NOT NULL, channel VARCHAR(32) NOT NULL,
 receiver VARCHAR(255) NOT NULL, template_code VARCHAR(96) NOT NULL, payload_json JSON,
 status VARCHAR(32) NOT NULL DEFAULT 'PENDING', retry_count INT NOT NULL DEFAULT 0, next_retry_at TIMESTAMP NULL,
 last_error TEXT, locked_at TIMESTAMP NULL, locked_by VARCHAR(64), sent_at TIMESTAMP NULL,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_approval_outbox_delivery(approval_id,channel,template_code),
 INDEX idx_approval_outbox(status,next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_tool_call (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, run_id BIGINT NOT NULL, task_id BIGINT, tool_code VARCHAR(96) NOT NULL,
 risk_level VARCHAR(32) NOT NULL, input_json JSON, output_json JSON, status VARCHAR(32) NOT NULL,
 latency_ms BIGINT, error_message TEXT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_tool_call_run(run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_data_source (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, source_code VARCHAR(96) NOT NULL UNIQUE, name VARCHAR(128) NOT NULL,
 dialect VARCHAR(32) NOT NULL DEFAULT 'MYSQL', connection_ref VARCHAR(255) NOT NULL, read_only_flag INT NOT NULL DEFAULT 1,
 status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_semantic_model (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, model_code VARCHAR(96) NOT NULL, version INT NOT NULL, name VARCHAR(128) NOT NULL,
 domain_code VARCHAR(96) NOT NULL, source_code VARCHAR(96) NOT NULL, catalog_json JSON NOT NULL,
 status VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED', created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_semantic_model(model_code,version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_metric_definition (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, metric_code VARCHAR(96) NOT NULL, version INT NOT NULL, name VARCHAR(128) NOT NULL,
 description TEXT, expression_sql TEXT NOT NULL, dimensions_json JSON, filters_json JSON,
 sensitivity_level VARCHAR(16) NOT NULL DEFAULT 'L0', status VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED',
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY uk_metric(metric_code,version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_sql_example (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, domain_code VARCHAR(96) NOT NULL, question TEXT NOT NULL, sql_text LONGTEXT NOT NULL,
 tags_json JSON, quality_score INT NOT NULL DEFAULT 80, status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_sql_query_run (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL DEFAULT 1, trace_id VARCHAR(64) NOT NULL,
 source_code VARCHAR(96) NOT NULL, semantic_model_code VARCHAR(96) NOT NULL, question TEXT NOT NULL,
 normalized_question TEXT, intent_json JSON, generated_sql LONGTEXT, final_sql LONGTEXT, risk_level VARCHAR(16),
 status VARCHAR(32) NOT NULL, execute_requested INT NOT NULL DEFAULT 0, max_rows INT NOT NULL DEFAULT 200,
 approval_id BIGINT, error_code VARCHAR(96), error_message TEXT, created_by BIGINT NOT NULL,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, completed_at TIMESTAMP NULL,
 INDEX idx_sql_run_status(status), INDEX idx_sql_run_creator(created_by,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_sql_candidate (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, query_run_id BIGINT NOT NULL, candidate_no INT NOT NULL,
 generator VARCHAR(64) NOT NULL, sql_text LONGTEXT NOT NULL, rationale TEXT, selected_flag INT NOT NULL DEFAULT 0,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, INDEX idx_sql_candidate_run(query_run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_sql_validation (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, query_run_id BIGINT NOT NULL, validator_code VARCHAR(96) NOT NULL,
 passed INT NOT NULL, risk_level VARCHAR(16) NOT NULL, details_json JSON, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_sql_validation_run(query_run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_sql_execution (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, query_run_id BIGINT NOT NULL, sql_hash VARCHAR(64) NOT NULL,
 row_count INT NOT NULL DEFAULT 0, columns_json JSON, result_json JSON, duration_ms BIGINT,
 truncated_flag INT NOT NULL DEFAULT 0, status VARCHAR(32) NOT NULL, error_message TEXT, executed_by BIGINT,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, INDEX idx_sql_execution_run(query_run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_sql_feedback (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, query_run_id BIGINT NOT NULL, rating INT, correction_sql LONGTEXT,
 comment TEXT, created_by BIGINT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_sql_feedback_run(query_run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sys_user(id,username,password,display_name,role,status) VALUES
 (1,'admin','admin123','招聘管理员','ADMIN',1),(2,'candidate','demo123','演示候选人','CANDIDATE',1)
ON DUPLICATE KEY UPDATE display_name=VALUES(display_name);
INSERT INTO recruit_job(id,title,department,city,salary_min,salary_max,experience_years,required_skills,description,status) VALUES
 (1,'Java 高级开发工程师','研发中心','上海',25000,35000,5,'Java,Spring Boot,MySQL,Redis,微服务','负责交易平台核心服务设计与开发，要求具备高并发系统经验。','PUBLISHED'),
 (2,'AI 产品经理','智能产品部','杭州',20000,30000,3,'产品设计,大模型,RAG,数据分析','负责智能招聘产品规划、用户研究与落地。','PUBLISHED')
ON DUPLICATE KEY UPDATE title=VALUES(title);
INSERT INTO talent_candidate(id,user_id,name,phone,email,city,years_of_experience,desired_position,skills,profile_summary) VALUES
 (1,2,'演示候选人','13800000000','demo@example.com','上海',5,'Java 高级开发工程师','Java,Spring Boot,MySQL,Redis','5 年 Java 后端经验，参与订单和交易系统开发。')
ON DUPLICATE KEY UPDATE name=VALUES(name);

INSERT INTO ai_agent_definition(id,agent_code,name,description,status,current_version,created_by) VALUES
 (101,'RESUME_RESEARCHER','简历证据研究 Agent','从候选人结构化资料中提取与岗位相关、可引用的事实和未知项','PUBLISHED',1,1),
 (102,'JOB_FIT_RESEARCHER','岗位适配研究 Agent','分析岗位能力要求，并将要求与候选证据逐项对齐','PUBLISHED',1,1),
 (103,'CANDIDATE_REPORT_WRITER','候选报告写作 Agent','只基于上游证据撰写结构化候选报告，不新增无证据事实','PUBLISHED',1,1),
 (104,'POLICY_AUDITOR','合规审计 Agent','检查敏感属性、越权内容、无证据结论和自动化决策风险','PUBLISHED',1,1)
ON DUPLICATE KEY UPDATE name=VALUES(name),description=VALUES(description),status=VALUES(status),current_version=VALUES(current_version);
INSERT INTO ai_agent_version(id,agent_code,version,system_prompt,model_policy,input_schema,output_schema,tool_policy,knowledge_scopes,max_model_calls,timeout_seconds,status) VALUES
 (201,'RESUME_RESEARCHER',1,'你是简历证据研究 Agent。只依据输入中的候选资料提取事实，不得编造。输出 JSON 对象，包含 summary、claims、evidence、unknowns；不要使用性别、年龄、婚育、民族、宗教等敏感属性。','STRUCTURED_EXTRACTION',JSON_OBJECT(),JSON_OBJECT('type','object','required',JSON_ARRAY('summary','claims','evidence','unknowns')),JSON_OBJECT('mode','NO_SIDE_EFFECT'),JSON_ARRAY('RESUME_EVIDENCE'),2,120,'PUBLISHED'),
 (202,'JOB_FIT_RESEARCHER',1,'你是岗位适配研究 Agent。将岗位要求和候选证据逐项对齐。只输出 JSON 对象，包含 summary、matchedRequirements、gaps、questionsToVerify。缺少证据时标记未知，不得自动淘汰候选人。','HIGH_ACCURACY_REASONING',JSON_OBJECT(),JSON_OBJECT('type','object','required',JSON_ARRAY('summary','matchedRequirements','gaps','questionsToVerify')),JSON_OBJECT('mode','NO_SIDE_EFFECT'),JSON_ARRAY('JOB_REQUIREMENT','RESUME_EVIDENCE'),2,120,'PUBLISHED'),
 (203,'CANDIDATE_REPORT_WRITER',1,'你是候选报告写作 Agent。只能使用上游 Artifact 中的证据写作。输出 JSON 对象，包含 executiveSummary、strengths、gaps、unknowns、evidenceReferences、recommendationNotice。recommendationNotice 必须说明 AI 仅供辅助并需要人工审核。','LONG_CONTEXT_WRITING',JSON_OBJECT(),JSON_OBJECT('type','object','required',JSON_ARRAY('executiveSummary','strengths','gaps','unknowns','evidenceReferences','recommendationNotice')),JSON_OBJECT('mode','NO_SIDE_EFFECT'),JSON_ARRAY('RESUME_EVIDENCE','JOB_REQUIREMENT'),2,120,'PUBLISHED'),
 (204,'POLICY_AUDITOR',1,'你是独立合规审计 Agent。检查上游候选报告中的敏感属性、无证据结论、绝对化语言、越权内容和自动淘汰倾向。输出 JSON 对象，包含 passed、issues、requiredActions、notice。','INDEPENDENT_AUDIT',JSON_OBJECT(),JSON_OBJECT('type','object','required',JSON_ARRAY('passed','issues','requiredActions','notice')),JSON_OBJECT('mode','NO_SIDE_EFFECT'),JSON_ARRAY('RECRUITMENT_POLICY'),2,120,'PUBLISHED')
ON DUPLICATE KEY UPDATE system_prompt=VALUES(system_prompt),output_schema=VALUES(output_schema),status=VALUES(status);
INSERT INTO ai_workflow_definition(id,workflow_code,name,description,status,current_version,created_by) VALUES
 (301,'CANDIDATE_REPORT','候选人综合报告工作流','简历证据和岗位要求并行研究，汇总后写作、合规审计并等待 HR 人工审批','PUBLISHED',1,1)
ON DUPLICATE KEY UPDATE name=VALUES(name),description=VALUES(description),status=VALUES(status),current_version=VALUES(current_version);
INSERT INTO ai_workflow_version(id,workflow_code,version,state_schema,input_schema,output_schema,budget_json,status) VALUES
 (401,'CANDIDATE_REPORT',1,JSON_OBJECT(),JSON_OBJECT('type','object','required',JSON_ARRAY('candidateId','jobId')),JSON_OBJECT('type','object'),JSON_OBJECT('maxNodeExecutions',30,'maxModelCalls',12,'maxDurationSeconds',900),'PUBLISHED')
ON DUPLICATE KEY UPDATE input_schema=VALUES(input_schema),budget_json=VALUES(budget_json),status=VALUES(status);
INSERT INTO ai_workflow_node(id,workflow_code,workflow_version,node_code,node_type,name,config_json,retry_json,timeout_seconds) VALUES
 (501,'CANDIDATE_REPORT',1,'START','START','开始',JSON_OBJECT('artifactType','WORKFLOW_INPUT'),JSON_OBJECT('maxAttempts',1),30),
 (502,'CANDIDATE_REPORT',1,'RESUME_RESEARCH','AGENT','简历证据研究',JSON_OBJECT('agentCode','RESUME_RESEARCHER','artifactType','RESUME_RESEARCH_NOTES'),JSON_OBJECT('maxAttempts',2),120),
 (503,'CANDIDATE_REPORT',1,'JOB_FIT_RESEARCH','AGENT','岗位适配研究',JSON_OBJECT('agentCode','JOB_FIT_RESEARCHER','artifactType','JOB_FIT_RESEARCH_NOTES'),JSON_OBJECT('maxAttempts',2),120),
 (504,'CANDIDATE_REPORT',1,'EVIDENCE_JOIN','JOIN','证据汇总',JSON_OBJECT('artifactType','CANDIDATE_EVIDENCE_BUNDLE'),JSON_OBJECT('maxAttempts',1),30),
 (505,'CANDIDATE_REPORT',1,'REPORT_WRITE','AGENT','报告写作',JSON_OBJECT('agentCode','CANDIDATE_REPORT_WRITER','artifactType','CANDIDATE_REPORT_DRAFT'),JSON_OBJECT('maxAttempts',2),120),
 (506,'CANDIDATE_REPORT',1,'POLICY_AUDIT','AGENT','政策与偏差审计',JSON_OBJECT('agentCode','POLICY_AUDITOR','artifactType','POLICY_AUDIT_RESULT'),JSON_OBJECT('maxAttempts',2),120),
 (507,'CANDIDATE_REPORT',1,'HUMAN_REVIEW','HUMAN_APPROVAL','HR 人工审核',JSON_OBJECT('title','请审核候选人综合报告','instruction','核对证据、未知项与合规审计结果后决定是否发布'),JSON_OBJECT('maxAttempts',1),86400),
 (508,'CANDIDATE_REPORT',1,'END','END','结束',JSON_OBJECT('artifactType','CANDIDATE_REPORT_FINAL'),JSON_OBJECT('maxAttempts',1),30)
ON DUPLICATE KEY UPDATE node_type=VALUES(node_type),name=VALUES(name),config_json=VALUES(config_json),retry_json=VALUES(retry_json),timeout_seconds=VALUES(timeout_seconds);
INSERT INTO ai_workflow_edge(id,workflow_code,workflow_version,from_node,to_node,condition_expression,priority) VALUES
 (601,'CANDIDATE_REPORT',1,'START','RESUME_RESEARCH','ALWAYS',10),
 (602,'CANDIDATE_REPORT',1,'START','JOB_FIT_RESEARCH','ALWAYS',20),
 (603,'CANDIDATE_REPORT',1,'RESUME_RESEARCH','EVIDENCE_JOIN','ALWAYS',10),
 (604,'CANDIDATE_REPORT',1,'JOB_FIT_RESEARCH','EVIDENCE_JOIN','ALWAYS',20),
 (605,'CANDIDATE_REPORT',1,'EVIDENCE_JOIN','REPORT_WRITE','ALWAYS',10),
 (606,'CANDIDATE_REPORT',1,'REPORT_WRITE','POLICY_AUDIT','ALWAYS',10),
 (607,'CANDIDATE_REPORT',1,'POLICY_AUDIT','HUMAN_REVIEW','ALWAYS',10),
 (608,'CANDIDATE_REPORT',1,'HUMAN_REVIEW','END','decision==APPROVE',10),
 (609,'CANDIDATE_REPORT',1,'HUMAN_REVIEW','END','decision==REJECT',20)
ON DUPLICATE KEY UPDATE condition_expression=VALUES(condition_expression),priority=VALUES(priority);

INSERT INTO ai_approval_policy(id,policy_code,version,name,approval_type,enabled,priority,condition_json,reviewer_group,required_approvals,sla_minutes,timeout_action,created_by) VALUES
 (701,'WORKFLOW_DEFAULT_REVIEW',1,'工作流默认人工审核','WORKFLOW',1,100,JSON_OBJECT('riskLevels',JSON_ARRAY('L1','L2','L3')),'HR_ADMIN',1,1440,'ESCALATE',1),
 (702,'TEXT_TO_SQL_SENSITIVE_REVIEW',1,'智能问数敏感数据审核','TEXT_TO_SQL',1,10,JSON_OBJECT('riskLevels',JSON_ARRAY('L2','L3')),'DATA_REVIEWER',1,240,'REJECT',1)
ON DUPLICATE KEY UPDATE name=VALUES(name),condition_json=VALUES(condition_json),reviewer_group=VALUES(reviewer_group),sla_minutes=VALUES(sla_minutes);
INSERT INTO ai_data_source(id,source_code,name,dialect,connection_ref,read_only_flag,status) VALUES
 (801,'RECRUITMENT_MAIN','招聘业务主库只读视图','MYSQL','spring.datasource',1,'ACTIVE')
ON DUPLICATE KEY UPDATE name=VALUES(name),read_only_flag=VALUES(read_only_flag),status=VALUES(status);
INSERT INTO ai_semantic_model(id,model_code,version,name,domain_code,source_code,catalog_json,status) VALUES
 (901,'RECRUITMENT_ANALYTICS',1,'招聘分析语义模型','RECRUITMENT','RECRUITMENT_MAIN',
  JSON_OBJECT('tables',JSON_ARRAY('recruit_job','talent_candidate','talent_resume','recruit_recommendation','ai_interview_invitation','ai_interview_session','candidate_activity_event'),
  'relationships',JSON_ARRAY('recruit_recommendation.job_id=recruit_job.id','recruit_recommendation.candidate_id=talent_candidate.id','ai_interview_session.job_id=recruit_job.id','ai_interview_session.candidate_id=talent_candidate.id'),
  'rules',JSON_ARRAY('只允许SELECT/CTE','默认最多200行','联系方式和简历原文属于敏感字段')),'PUBLISHED')
ON DUPLICATE KEY UPDATE catalog_json=VALUES(catalog_json),status=VALUES(status);
INSERT INTO ai_metric_definition(id,metric_code,version,name,description,expression_sql,dimensions_json,filters_json,sensitivity_level,status) VALUES
 (1001,'PUBLISHED_JOB_COUNT',1,'在招职位数','状态为PUBLISHED的职位数量','COUNT(*)',JSON_ARRAY('department','city'),JSON_OBJECT('status','PUBLISHED'),'L0','PUBLISHED'),
 (1002,'CANDIDATE_COUNT',1,'候选人数','人才库候选人总量','COUNT(*)',JSON_ARRAY('city','desired_position'),JSON_OBJECT(),'L0','PUBLISHED'),
 (1003,'RECOMMENDATION_AVG_SCORE',1,'推荐平均分','按职位统计AI岗位推荐平均分','AVG(score)',JSON_ARRAY('job_id'),JSON_OBJECT(),'L1','PUBLISHED')
ON DUPLICATE KEY UPDATE name=VALUES(name),description=VALUES(description),expression_sql=VALUES(expression_sql),status=VALUES(status);
