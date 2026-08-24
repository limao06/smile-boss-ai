CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password VARCHAR(128) NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    role VARCHAR(32) NOT NULL,
    status INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS recruit_job (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(128) NOT NULL,
    department VARCHAR(128),
    city VARCHAR(64),
    salary_min INT,
    salary_max INT,
    experience_years INT DEFAULT 0,
    required_skills VARCHAR(1000),
    description TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS talent_candidate (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    name VARCHAR(64) NOT NULL,
    phone VARCHAR(32),
    email VARCHAR(128),
    city VARCHAR(64),
    years_of_experience INT DEFAULT 0,
    desired_position VARCHAR(128),
    skills VARCHAR(1000),
    profile_summary TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS talent_resume (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    candidate_id BIGINT NOT NULL,
    file_name VARCHAR(255),
    file_hash VARCHAR(64),
    raw_text TEXT,
    structured_json TEXT,
    parse_status VARCHAR(32) NOT NULL,
    completeness_score INT DEFAULT 0,
    completeness_json TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- AI 简历工作台：主简历只保存当前发布版本指针，所有内容变更都追加版本，便于审计和回滚。
CREATE TABLE IF NOT EXISTS candidate_resume_workspace (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    candidate_id BIGINT NOT NULL UNIQUE,
    resume_name VARCHAR(128) NOT NULL,
    current_version_id BIGINT,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS candidate_resume_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    version_type VARCHAR(32) NOT NULL DEFAULT 'MASTER',
    source_type VARCHAR(32) NOT NULL,
    source_resume_id BIGINT,
    target_job_id BIGINT,
    content_json LONGTEXT NOT NULL,
    quality_score INT NOT NULL DEFAULT 0,
    change_summary VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(workspace_id, version_no)
);

CREATE TABLE IF NOT EXISTS resume_optimization_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    candidate_id BIGINT NOT NULL,
    workspace_id BIGINT NOT NULL,
    source_version_id BIGINT NOT NULL,
    target_job_id BIGINT,
    status VARCHAR(32) NOT NULL,
    score_before INT NOT NULL DEFAULT 0,
    score_after INT NOT NULL DEFAULT 0,
    suggestions_json LONGTEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    applied_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS resume_greeting_generation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    candidate_id BIGINT NOT NULL,
    resume_version_id BIGINT NOT NULL,
    job_id BIGINT NOT NULL,
    tone VARCHAR(32) NOT NULL,
    content_json LONGTEXT NOT NULL,
    evidence_json LONGTEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS recruit_recommendation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    candidate_id BIGINT NOT NULL,
    job_id BIGINT NOT NULL,
    score INT NOT NULL,
    result_json TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(candidate_id, job_id)
);

CREATE TABLE IF NOT EXISTS mock_interview_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    candidate_id BIGINT NOT NULL,
    job_id BIGINT,
    interview_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    current_round INT DEFAULT 0,
    score INT,
    report_json TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS mock_interview_turn (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    round_no INT NOT NULL,
    question TEXT NOT NULL,
    answer TEXT,
    evaluation_json TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_interview_invitation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    candidate_id BIGINT NOT NULL,
    job_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    duration_minutes INT NOT NULL DEFAULT 25,
    expires_at TIMESTAMP,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_interview_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    invitation_id BIGINT NOT NULL,
    candidate_id BIGINT NOT NULL,
    job_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    current_stage VARCHAR(32) NOT NULL,
    current_round INT DEFAULT 0,
    plan_json TEXT,
    report_json TEXT,
    ai_score INT,
    hr_decision VARCHAR(32),
    hr_comment TEXT,
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_interview_turn (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    round_no INT NOT NULL,
    competency VARCHAR(64),
    question TEXT NOT NULL,
    answer TEXT,
    score INT,
    evidence TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS candidate_activity_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    candidate_id BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    object_type VARCHAR(64),
    object_id BIGINT,
    metadata_json TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_model_call_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider VARCHAR(32),
    model VARCHAR(128),
    scene VARCHAR(64),
    latency_ms BIGINT,
    status VARCHAR(32),
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_agent_definition (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_code VARCHAR(96) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    current_version INT NOT NULL DEFAULT 1,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_agent_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_code VARCHAR(96) NOT NULL,
    version INT NOT NULL,
    system_prompt TEXT NOT NULL,
    model_policy VARCHAR(64) NOT NULL DEFAULT 'HIGH_ACCURACY_REASONING',
    input_schema TEXT,
    output_schema TEXT,
    tool_policy TEXT,
    knowledge_scopes TEXT,
    max_model_calls INT NOT NULL DEFAULT 2,
    timeout_seconds INT NOT NULL DEFAULT 120,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(agent_code, version)
);

CREATE TABLE IF NOT EXISTS ai_workflow_definition (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_code VARCHAR(96) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    current_version INT NOT NULL DEFAULT 1,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_workflow_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_code VARCHAR(96) NOT NULL,
    version INT NOT NULL,
    state_schema TEXT,
    input_schema TEXT,
    output_schema TEXT,
    budget_json TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(workflow_code, version)
);

CREATE TABLE IF NOT EXISTS ai_workflow_node (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_code VARCHAR(96) NOT NULL,
    workflow_version INT NOT NULL,
    node_code VARCHAR(96) NOT NULL,
    node_type VARCHAR(32) NOT NULL,
    name VARCHAR(128) NOT NULL,
    config_json TEXT,
    retry_json TEXT,
    timeout_seconds INT NOT NULL DEFAULT 120,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(workflow_code, workflow_version, node_code)
);

CREATE TABLE IF NOT EXISTS ai_workflow_edge (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_code VARCHAR(96) NOT NULL,
    workflow_version INT NOT NULL,
    from_node VARCHAR(96) NOT NULL,
    to_node VARCHAR(96) NOT NULL,
    condition_expression VARCHAR(512) NOT NULL DEFAULT 'ALWAYS',
    priority INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_workflow_run (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    workflow_code VARCHAR(96) NOT NULL,
    workflow_version INT NOT NULL,
    business_type VARCHAR(64),
    business_id VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    state_json TEXT NOT NULL,
    state_version INT NOT NULL DEFAULT 0,
    budget_json TEXT,
    used_budget_json TEXT,
    trace_id VARCHAR(64) NOT NULL,
    created_by BIGINT NOT NULL,
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deadline_at TIMESTAMP,
    completed_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_workflow_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id BIGINT NOT NULL,
    node_code VARCHAR(96) NOT NULL,
    node_type VARCHAR(32) NOT NULL,
    agent_code VARCHAR(96),
    agent_version INT,
    status VARCHAR(32) NOT NULL,
    attempt_no INT NOT NULL DEFAULT 1,
    input_artifact_ids TEXT,
    output_artifact_ids TEXT,
    idempotency_key VARCHAR(192) NOT NULL UNIQUE,
    scheduled_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    error_code VARCHAR(96),
    error_message TEXT
);

CREATE TABLE IF NOT EXISTS ai_artifact (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    run_id BIGINT NOT NULL,
    task_id BIGINT,
    artifact_type VARCHAR(96) NOT NULL,
    schema_version VARCHAR(32) NOT NULL DEFAULT 'v1',
    content_json TEXT NOT NULL,
    evidence_ids TEXT,
    confidence DECIMAL(6,5),
    status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    producer_type VARCHAR(32) NOT NULL,
    producer_code VARCHAR(96) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id BIGINT NOT NULL,
    task_id BIGINT,
    message_type VARCHAR(32) NOT NULL,
    sender VARCHAR(96) NOT NULL,
    receiver VARCHAR(96),
    artifact_id BIGINT,
    payload_json TEXT,
    idempotency_key VARCHAR(192) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_workflow_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id BIGINT NOT NULL,
    task_id BIGINT,
    event_type VARCHAR(64) NOT NULL,
    node_code VARCHAR(96),
    payload_json TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_workflow_checkpoint (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id BIGINT NOT NULL,
    state_version INT NOT NULL,
    node_code VARCHAR(96),
    state_json TEXT NOT NULL,
    artifact_ids TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(run_id, state_version)
);

CREATE TABLE IF NOT EXISTS ai_human_approval (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL UNIQUE,
    node_code VARCHAR(96) NOT NULL,
    approval_type VARCHAR(64) NOT NULL DEFAULT 'WORKFLOW',
    origin_type VARCHAR(64) NOT NULL DEFAULT 'WORKFLOW_RUN',
    origin_id BIGINT,
    request_token VARCHAR(64),
    policy_code VARCHAR(96),
    policy_version INT,
    risk_level VARCHAR(16) NOT NULL DEFAULT 'L1',
    reviewer_group VARCHAR(96) NOT NULL DEFAULT 'HR_ADMIN',
    assigned_to BIGINT,
    claimed_at TIMESTAMP,
    lease_expires_at TIMESTAMP,
    due_at TIMESTAMP,
    timeout_action VARCHAR(32) NOT NULL DEFAULT 'ESCALATE',
    title VARCHAR(255) NOT NULL,
    instruction TEXT,
    request_payload_json TEXT,
    decision_payload_json TEXT,
    input_snapshot_hash VARCHAR(64),
    resume_checkpoint_id BIGINT,
    decision_version INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    decision VARCHAR(32),
    comment TEXT,
    requested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    decided_by BIGINT,
    decided_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_approval_policy (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    policy_code VARCHAR(96) NOT NULL,
    version INT NOT NULL,
    name VARCHAR(128) NOT NULL,
    approval_type VARCHAR(64) NOT NULL,
    enabled INT NOT NULL DEFAULT 1,
    priority INT NOT NULL DEFAULT 100,
    condition_json TEXT,
    reviewer_group VARCHAR(96) NOT NULL DEFAULT 'HR_ADMIN',
    required_approvals INT NOT NULL DEFAULT 1,
    sla_minutes INT NOT NULL DEFAULT 1440,
    timeout_action VARCHAR(32) NOT NULL DEFAULT 'ESCALATE',
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(policy_code, version)
);

CREATE TABLE IF NOT EXISTS ai_human_approval_action (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    approval_id BIGINT NOT NULL,
    action_type VARCHAR(32) NOT NULL,
    actor_id BIGINT,
    from_status VARCHAR(32),
    to_status VARCHAR(32),
    payload_json TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_approval_notification_outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    approval_id BIGINT NOT NULL,
    channel VARCHAR(32) NOT NULL,
    receiver VARCHAR(255) NOT NULL,
    template_code VARCHAR(96) NOT NULL,
    payload_json TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP,
    last_error TEXT,
    locked_at TIMESTAMP,
    locked_by VARCHAR(64),
    sent_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(approval_id, channel, template_code)
);

CREATE TABLE IF NOT EXISTS ai_tool_call (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id BIGINT NOT NULL,
    task_id BIGINT,
    tool_code VARCHAR(96) NOT NULL,
    risk_level VARCHAR(32) NOT NULL,
    input_json TEXT,
    output_json TEXT,
    status VARCHAR(32) NOT NULL,
    latency_ms BIGINT,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_data_source (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_code VARCHAR(96) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    dialect VARCHAR(32) NOT NULL DEFAULT 'MYSQL',
    connection_ref VARCHAR(255) NOT NULL,
    read_only_flag INT NOT NULL DEFAULT 1,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_semantic_model (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    model_code VARCHAR(96) NOT NULL,
    version INT NOT NULL,
    name VARCHAR(128) NOT NULL,
    domain_code VARCHAR(96) NOT NULL,
    source_code VARCHAR(96) NOT NULL,
    catalog_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(model_code, version)
);

CREATE TABLE IF NOT EXISTS ai_metric_definition (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    metric_code VARCHAR(96) NOT NULL,
    version INT NOT NULL,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    expression_sql TEXT NOT NULL,
    dimensions_json TEXT,
    filters_json TEXT,
    sensitivity_level VARCHAR(16) NOT NULL DEFAULT 'L0',
    status VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(metric_code, version)
);

CREATE TABLE IF NOT EXISTS ai_sql_example (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    domain_code VARCHAR(96) NOT NULL,
    question TEXT NOT NULL,
    sql_text TEXT NOT NULL,
    tags_json TEXT,
    quality_score INT NOT NULL DEFAULT 80,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_sql_query_run (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    trace_id VARCHAR(64) NOT NULL,
    source_code VARCHAR(96) NOT NULL,
    semantic_model_code VARCHAR(96) NOT NULL,
    question TEXT NOT NULL,
    normalized_question TEXT,
    intent_json TEXT,
    generated_sql TEXT,
    final_sql TEXT,
    risk_level VARCHAR(16),
    status VARCHAR(32) NOT NULL,
    execute_requested INT NOT NULL DEFAULT 0,
    max_rows INT NOT NULL DEFAULT 200,
    approval_id BIGINT,
    error_code VARCHAR(96),
    error_message TEXT,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_sql_candidate (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    query_run_id BIGINT NOT NULL,
    candidate_no INT NOT NULL,
    generator VARCHAR(64) NOT NULL,
    sql_text TEXT NOT NULL,
    rationale TEXT,
    selected_flag INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_sql_validation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    query_run_id BIGINT NOT NULL,
    validator_code VARCHAR(96) NOT NULL,
    passed INT NOT NULL,
    risk_level VARCHAR(16) NOT NULL,
    details_json TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_sql_execution (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    query_run_id BIGINT NOT NULL,
    sql_hash VARCHAR(64) NOT NULL,
    row_count INT NOT NULL DEFAULT 0,
    columns_json TEXT,
    result_json TEXT,
    duration_ms BIGINT,
    truncated_flag INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    error_message TEXT,
    executed_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_sql_feedback (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    query_run_id BIGINT NOT NULL,
    rating INT,
    correction_sql TEXT,
    comment TEXT,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
