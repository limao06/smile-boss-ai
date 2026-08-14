package com.smileboss.approval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smileboss.common.BizException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 人工审核领域服务。
 *
 * <p>统一处理认领、乐观锁决定和动作审计，避免工作流与 Text-to-SQL 各自实现一套
 * 并发控制。业务服务仍负责审核前后的领域动作，例如恢复工作流或执行已批准的 SQL。</p>
 */
@Service
public class HumanApprovalService {
    private static final Set<String> SUPPORTED_DECISIONS = Set.of("APPROVE", "REJECT");
    private static final int MIN_LEASE_MINUTES = 5;
    private static final int MAX_LEASE_MINUTES = 240;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ApprovalNotificationOutboxService notificationOutboxService;

    public HumanApprovalService(JdbcTemplate jdbcTemplate,
                                ObjectMapper objectMapper,
                                ApprovalNotificationOutboxService notificationOutboxService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.notificationOutboxService = notificationOutboxService;
    }

    public Map<String, Object> findById(long approvalId) {
        return queryOne("SELECT * FROM ai_human_approval WHERE id=?", "人工审核任务不存在", approvalId);
    }

    public Map<String, Object> findByTaskId(long taskId) {
        return queryOne("SELECT * FROM ai_human_approval WHERE task_id=?", "人工审核任务不存在", taskId);
    }

    /**
     * 使用带租约的认领而不是永久占用。租约过期后，其他审核人可以重新认领任务。
     */
    @Transactional
    public Map<String, Object> claim(long approvalId, int requestedLeaseMinutes, long userId) {
        Map<String, Object> approval = findById(approvalId);
        requirePending(approval);

        int leaseMinutes = Math.max(MIN_LEASE_MINUTES, Math.min(requestedLeaseMinutes, MAX_LEASE_MINUTES));
        int affectedRows = jdbcTemplate.update("""
                UPDATE ai_human_approval
                SET assigned_to=?, claimed_at=CURRENT_TIMESTAMP, lease_expires_at=?,
                    decision_version=decision_version+1
                WHERE id=? AND status='PENDING'
                  AND (assigned_to IS NULL OR assigned_to=? OR lease_expires_at<CURRENT_TIMESTAMP)
                """, userId, Timestamp.valueOf(LocalDateTime.now().plusMinutes(leaseMinutes)), approvalId, userId);
        if (affectedRows != 1) {
            throw new BizException("该审核任务已被其他审核人认领");
        }

        recordAction(approvalId, "CLAIM", userId, "PENDING", "PENDING", Map.of("leaseMinutes", leaseMinutes));
        return findById(approvalId);
    }

    /**
     * 用 decision_version 做 CAS 更新，保证同一审核只产生一个最终决定。
     */
    @Transactional
    public void decide(long approvalId, int expectedVersion, String decisionValue, String comment, long userId) {
        String decision = normalizeDecision(decisionValue);
        int affectedRows = jdbcTemplate.update("""
                UPDATE ai_human_approval
                SET status='DECIDED', decision=?, comment=?, decided_by=?, decided_at=CURRENT_TIMESTAMP,
                    decision_payload_json=?, decision_version=decision_version+1
                WHERE id=? AND status='PENDING' AND decision_version=?
                """, decision, normalizeComment(comment), userId,
                toJson(Map.of("decision", decision, "comment", normalizeComment(comment))),
                approvalId, expectedVersion);
        if (affectedRows != 1) {
            throw new BizException("审核任务已被其他审核人处理，请刷新后重试");
        }
        recordAction(approvalId, "DECIDE", userId, "PENDING", "DECIDED",
                Map.of("decision", decision, "comment", normalizeComment(comment)));
        notificationOutboxService.cancelPending(approvalId);
    }

    /**
     * 记录审核请求并写入钉钉通知 Outbox。所有创建审核任务的业务都走这个入口，
     * 从而避免新增 Agent 或新审批类型时漏发通知。
     */
    @Transactional
    public void recordRequest(long approvalId, Long requesterId, Object payload) {
        recordAction(approvalId, "REQUEST", requesterId, null, "PENDING", payload);
        notificationOutboxService.enqueueDingTalk(approvalId);
    }

    public void requirePending(Map<String, Object> approval) {
        if (!"PENDING".equals(stringValue(approval.get("status")))) {
            throw new BizException("人工审核任务已经处理");
        }
    }

    public String normalizeDecision(String decisionValue) {
        String decision = decisionValue == null ? "" : decisionValue.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_DECISIONS.contains(decision)) {
            throw new BizException("人工决定只支持 APPROVE 或 REJECT");
        }
        return decision;
    }

    public void recordAction(long approvalId, String actionType, Long actorId,
                             String fromStatus, String toStatus, Object payload) {
        jdbcTemplate.update("""
                INSERT INTO ai_human_approval_action(
                    approval_id, action_type, actor_id, from_status, to_status, payload_json)
                VALUES(?,?,?,?,?,?)
                """, approvalId, actionType, actorId, fromStatus, toStatus, toJson(payload));
    }

    private Map<String, Object> queryOne(String sql, String notFoundMessage, Object... parameters) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, parameters);
        if (rows.isEmpty()) {
            throw new BizException(notFoundMessage);
        }
        return rows.get(0);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new BizException("审核数据序列化失败：" + exception.getOriginalMessage());
        }
    }

    private static String normalizeComment(String comment) {
        return comment == null ? "" : comment.trim();
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
