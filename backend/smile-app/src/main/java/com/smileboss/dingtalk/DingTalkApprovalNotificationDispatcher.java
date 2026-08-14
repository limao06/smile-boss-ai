package com.smileboss.dingtalk;

import com.smileboss.approval.HumanApprovalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 钉钉审批通知投递器。
 *
 * <p>状态机：PENDING/RETRY → SENDING → SENT；失败后指数退避，达到上限进入 FAILED。
 * 多实例通过条件更新抢占单条消息，进程异常遗留的 SENDING 会在锁超时后恢复。</p>
 */
@Component
public class DingTalkApprovalNotificationDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(DingTalkApprovalNotificationDispatcher.class);
    private static final String CHANNEL = "DINGTALK";

    private final JdbcTemplate jdbcTemplate;
    private final DingTalkProperties properties;
    private final DingTalkRobotClient robotClient;
    private final DingTalkApprovalMessageFactory messageFactory;
    private final HumanApprovalService approvalService;
    private final String workerId = UUID.randomUUID().toString();

    public DingTalkApprovalNotificationDispatcher(JdbcTemplate jdbcTemplate,
                                                  DingTalkProperties properties,
                                                  DingTalkRobotClient robotClient,
                                                  DingTalkApprovalMessageFactory messageFactory,
                                                  HumanApprovalService approvalService) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.robotClient = robotClient;
        this.messageFactory = messageFactory;
        this.approvalService = approvalService;
    }

    @Scheduled(
            fixedDelayString = "${smile.dingtalk.dispatch-interval-millis:5000}",
            initialDelayString = "${smile.dingtalk.initial-delay-millis:3000}")
    public void scheduledDispatch() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            dispatchOnce();
        } catch (RuntimeException exception) {
            LOGGER.error("钉钉审核通知批次投递失败：{}", safeMessage(exception));
        }
    }

    /** 每次最多处理一个小批次，避免机器人限流时阻塞调度线程。 */
    public int dispatchOnce() {
        if (!properties.isEnabled()) {
            return 0;
        }
        recoverStaleClaims();
        int batchSize = Math.max(1, Math.min(properties.getBatchSize(), 100));
        List<Map<String, Object>> candidates = jdbcTemplate.queryForList("""
                SELECT id, approval_id, retry_count
                FROM ai_approval_notification_outbox
                WHERE channel=? AND status IN ('PENDING','RETRY')
                  AND (next_retry_at IS NULL OR next_retry_at<=CURRENT_TIMESTAMP)
                ORDER BY id
                LIMIT ?
                """, CHANNEL, batchSize);

        int delivered = 0;
        for (Map<String, Object> candidate : candidates) {
            long outboxId = longValue(candidate.get("id"));
            if (!claim(outboxId)) {
                continue;
            }
            if (deliverClaimed(outboxId, longValue(candidate.get("approval_id")),
                    intValue(candidate.get("retry_count")))) {
                delivered++;
            }
        }
        return delivered;
    }

    private boolean claim(long outboxId) {
        int updated = jdbcTemplate.update("""
                UPDATE ai_approval_notification_outbox
                SET status='SENDING', locked_at=CURRENT_TIMESTAMP, locked_by=?
                WHERE id=? AND channel=? AND status IN ('PENDING','RETRY')
                  AND (next_retry_at IS NULL OR next_retry_at<=CURRENT_TIMESTAMP)
                """, workerId, outboxId, CHANNEL);
        return updated == 1;
    }

    private boolean deliverClaimed(long outboxId, long approvalId, int retryCount) {
        List<Map<String, Object>> approvals = jdbcTemplate.queryForList(
                "SELECT * FROM ai_human_approval WHERE id=?", approvalId);
        if (approvals.isEmpty() || !"PENDING".equals(String.valueOf(approvals.get(0).get("status")))) {
            markCancelled(outboxId);
            return false;
        }

        try {
            DingTalkApprovalMessageFactory.Message message = messageFactory.create(approvals.get(0));
            robotClient.sendMarkdown(message.title(), message.markdown());
        } catch (RuntimeException exception) {
            markFailure(outboxId, approvalId, retryCount + 1, exception);
            return false;
        }

        int updated = jdbcTemplate.update("""
                UPDATE ai_approval_notification_outbox
                SET status='SENT', sent_at=CURRENT_TIMESTAMP, last_error=NULL,
                    locked_at=NULL, locked_by=NULL
                WHERE id=? AND status='SENDING' AND locked_by=?
                """, outboxId, workerId);
        if (updated != 1) {
            LOGGER.error("钉钉消息已发送但 Outbox 状态更新失败，approvalId={}，outboxId={}", approvalId, outboxId);
            return false;
        }
        recordNotificationActionSafely(approvalId, "NOTIFY_DINGTALK_SENT",
                Map.of("outboxId", outboxId));
        return true;
    }

    private void markFailure(long outboxId, long approvalId, int failureCount, RuntimeException exception) {
        int maxRetries = Math.max(1, properties.getMaxRetries());
        boolean exhausted = failureCount >= maxRetries;
        LocalDateTime nextAttempt = LocalDateTime.now().plusSeconds(backoffSeconds(failureCount));
        jdbcTemplate.update("""
                UPDATE ai_approval_notification_outbox
                SET status=?, retry_count=?, next_retry_at=?, last_error=?, locked_at=NULL, locked_by=NULL
                WHERE id=? AND status='SENDING' AND locked_by=?
                """, exhausted ? "FAILED" : "RETRY", failureCount,
                exhausted ? null : Timestamp.valueOf(nextAttempt), safeMessage(exception), outboxId, workerId);
        recordNotificationActionSafely(approvalId,
                exhausted ? "NOTIFY_DINGTALK_FAILED" : "NOTIFY_DINGTALK_RETRY",
                Map.of("outboxId", outboxId, "retryCount", failureCount));
        LOGGER.warn("钉钉审核通知投递失败，approvalId={}，outboxId={}，retryCount={}：{}",
                approvalId, outboxId, failureCount, safeMessage(exception));
    }

    private long backoffSeconds(int failureCount) {
        long base = Math.max(1, properties.getRetryBaseSeconds());
        int exponent = Math.min(Math.max(0, failureCount - 1), 20);
        long delay = base * (1L << exponent);
        return Math.min(delay, Math.max(base, properties.getRetryMaximumSeconds()));
    }

    private void markCancelled(long outboxId) {
        jdbcTemplate.update("""
                UPDATE ai_approval_notification_outbox
                SET status='CANCELLED', locked_at=NULL, locked_by=NULL
                WHERE id=? AND status='SENDING' AND locked_by=?
                """, outboxId, workerId);
    }

    private void recoverStaleClaims() {
        LocalDateTime staleBefore = LocalDateTime.now()
                .minusSeconds(Math.max(30, properties.getStaleLockSeconds()));
        jdbcTemplate.update("""
                UPDATE ai_approval_notification_outbox
                SET status='RETRY', next_retry_at=CURRENT_TIMESTAMP, locked_at=NULL, locked_by=NULL,
                    last_error='投递进程异常退出，已自动恢复'
                WHERE channel=? AND status='SENDING' AND locked_at<?
                """, CHANNEL, Timestamp.valueOf(staleBefore));
    }

    private void recordNotificationActionSafely(long approvalId, String actionType, Map<String, Object> payload) {
        try {
            approvalService.recordAction(approvalId, actionType, null,
                    "PENDING", "PENDING", payload);
        } catch (RuntimeException exception) {
            // 通知主状态已经落库。审计失败只告警，不能把已成功发送的外部消息再次入队。
            LOGGER.error("钉钉通知审计写入失败，approvalId={}，actionType={}：{}",
                    approvalId, actionType, safeMessage(exception));
        }
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        String sanitized = message
                .replaceAll("(?i)access_token=[^&\\s]+", "access_token=***")
                .replaceAll("(?i)sign=[^&\\s]+", "sign=***");
        return sanitized.length() <= 1000 ? sanitized : sanitized.substring(0, 1000);
    }

    private static long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }

    private static int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }
}
