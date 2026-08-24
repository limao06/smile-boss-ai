-- 从钉钉 HITL 版本升级到 AI 简历工作台版本时执行一次。
-- 全新数据库只需执行最新版 init-mysql.sql，不要执行本迁移文件。
USE smile_boss_ai;

-- 旧版本的 file_hash 是全局唯一，改为候选人范围内唯一，避免不同用户上传相同公开模板时互相影响。
ALTER TABLE talent_resume DROP INDEX file_hash;
ALTER TABLE talent_resume
 ADD UNIQUE INDEX uk_resume_candidate_hash(candidate_id,file_hash);

CREATE TABLE IF NOT EXISTS candidate_resume_workspace (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 candidate_id BIGINT NOT NULL,
 resume_name VARCHAR(128) NOT NULL,
 current_version_id BIGINT,
 status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 UNIQUE KEY uk_resume_workspace_candidate(candidate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS candidate_resume_version (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 workspace_id BIGINT NOT NULL,
 version_no INT NOT NULL,
 version_type VARCHAR(32) NOT NULL DEFAULT 'MASTER',
 source_type VARCHAR(32) NOT NULL,
 source_resume_id BIGINT,
 target_job_id BIGINT,
 content_json JSON NOT NULL,
 quality_score INT NOT NULL DEFAULT 0,
 change_summary VARCHAR(500),
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_resume_workspace_version(workspace_id,version_no),
 INDEX idx_resume_version_target_job(target_job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS resume_optimization_task (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 candidate_id BIGINT NOT NULL,
 workspace_id BIGINT NOT NULL,
 source_version_id BIGINT NOT NULL,
 target_job_id BIGINT,
 status VARCHAR(32) NOT NULL,
 score_before INT NOT NULL DEFAULT 0,
 score_after INT NOT NULL DEFAULT 0,
 suggestions_json JSON NOT NULL,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 applied_at TIMESTAMP NULL,
 INDEX idx_resume_optimization_candidate(candidate_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS resume_greeting_generation (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 candidate_id BIGINT NOT NULL,
 resume_version_id BIGINT NOT NULL,
 job_id BIGINT NOT NULL,
 tone VARCHAR(32) NOT NULL,
 content_json JSON NOT NULL,
 evidence_json JSON NOT NULL,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_resume_greeting_candidate(candidate_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
