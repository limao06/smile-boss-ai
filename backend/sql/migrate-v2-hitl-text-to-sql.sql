-- 仅用于从早期 SmileBoss 数据库升级到 HITL + Text-to-SQL 版本，执行一次。
-- 新建数据库请直接执行 init-mysql.sql，不要执行本文件。
USE smile_boss_ai;

ALTER TABLE ai_human_approval
 ADD COLUMN approval_type VARCHAR(64) NOT NULL DEFAULT 'WORKFLOW' AFTER node_code,
 ADD COLUMN origin_type VARCHAR(64) NOT NULL DEFAULT 'WORKFLOW_RUN' AFTER approval_type,
 ADD COLUMN origin_id BIGINT NULL AFTER origin_type,
 ADD COLUMN request_token VARCHAR(64) NULL AFTER origin_id,
 ADD COLUMN policy_code VARCHAR(96) NULL AFTER request_token,
 ADD COLUMN policy_version INT NULL AFTER policy_code,
 ADD COLUMN risk_level VARCHAR(16) NOT NULL DEFAULT 'L1' AFTER policy_version,
 ADD COLUMN reviewer_group VARCHAR(96) NOT NULL DEFAULT 'HR_ADMIN' AFTER risk_level,
 ADD COLUMN assigned_to BIGINT NULL AFTER reviewer_group,
 ADD COLUMN claimed_at TIMESTAMP NULL AFTER assigned_to,
 ADD COLUMN lease_expires_at TIMESTAMP NULL AFTER claimed_at,
 ADD COLUMN due_at TIMESTAMP NULL AFTER lease_expires_at,
 ADD COLUMN timeout_action VARCHAR(32) NOT NULL DEFAULT 'ESCALATE' AFTER due_at,
 ADD COLUMN request_payload_json JSON NULL AFTER instruction,
 ADD COLUMN decision_payload_json JSON NULL AFTER request_payload_json,
 ADD COLUMN input_snapshot_hash VARCHAR(64) NULL AFTER decision_payload_json,
 ADD COLUMN resume_checkpoint_id BIGINT NULL AFTER input_snapshot_hash,
 ADD COLUMN decision_version INT NOT NULL DEFAULT 0 AFTER resume_checkpoint_id,
 ADD COLUMN expires_at TIMESTAMP NULL AFTER requested_at,
 ADD INDEX idx_human_approval_assignee(assigned_to,status),
 ADD INDEX idx_human_approval_origin(origin_type,origin_id);

-- 完成上述 ALTER 后，再执行最新版 init-mysql.sql。
-- init-mysql.sql 会以 CREATE TABLE IF NOT EXISTS 创建其余策略、审计和 Text-to-SQL 表并写入基础配置。
