-- 从 HITL + Text-to-SQL 版本升级到钉钉可靠通知版本时执行一次。
-- 全新数据库只需执行最新版 init-mysql.sql，不要执行本迁移文件。
USE smile_boss_ai;

ALTER TABLE ai_approval_notification_outbox
 ADD COLUMN last_error TEXT NULL AFTER next_retry_at,
 ADD COLUMN locked_at TIMESTAMP NULL AFTER last_error,
 ADD COLUMN locked_by VARCHAR(64) NULL AFTER locked_at,
 ADD UNIQUE INDEX uk_approval_outbox_delivery(approval_id,channel,template_code);

