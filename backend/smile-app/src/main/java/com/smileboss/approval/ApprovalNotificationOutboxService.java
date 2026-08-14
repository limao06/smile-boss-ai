package com.smileboss.approval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smileboss.common.BizException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 人工审核通知 Outbox。
 *
 * <p>业务事务只负责写入待投递事件，绝不在创建审批的线程中调用钉钉网络接口。
 * 唯一键保证同一审批的同一模板最多入队一次。</p>
 */
@Service
public class ApprovalNotificationOutboxService {
    public static final String DINGTALK_CHANNEL = "DINGTALK";
    public static final String APPROVAL_REQUESTED_TEMPLATE = "HUMAN_APPROVAL_REQUESTED_V1";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ApprovalNotificationOutboxService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void enqueueDingTalk(long approvalId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT reviewer_group FROM ai_human_approval WHERE id=?", approvalId);
        if (rows.isEmpty()) {
            throw new BizException("人工审核任务不存在，无法创建钉钉通知");
        }
        String reviewerGroup = String.valueOf(rows.get(0).getOrDefault("reviewer_group", "HR_ADMIN"));
        try {
            jdbcTemplate.update("""
                    INSERT INTO ai_approval_notification_outbox(
                        approval_id, channel, receiver, template_code, payload_json, status, next_retry_at)
                    VALUES(?,?,?,?,?,'PENDING',CURRENT_TIMESTAMP)
                    """, approvalId, DINGTALK_CHANNEL, reviewerGroup, APPROVAL_REQUESTED_TEMPLATE,
                    toJson(Map.of("approvalId", approvalId)));
        } catch (DuplicateKeyException ignored) {
            // 重试创建审批事件时保持幂等，不重复轰炸审核群。
        }
    }

    public void cancelPending(long approvalId) {
        jdbcTemplate.update("""
                UPDATE ai_approval_notification_outbox
                SET status='CANCELLED', locked_at=NULL, locked_by=NULL
                WHERE approval_id=? AND status IN ('PENDING','RETRY','SENDING')
                """, approvalId);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BizException("审核通知序列化失败：" + exception.getOriginalMessage());
        }
    }
}
