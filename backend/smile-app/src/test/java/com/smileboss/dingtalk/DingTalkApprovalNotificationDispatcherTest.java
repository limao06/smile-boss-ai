package com.smileboss.dingtalk;

import com.smileboss.approval.ApprovalNotificationOutboxService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "smile.dingtalk.enabled=true",
        "smile.dingtalk.webhook=https://oapi.dingtalk.com/robot/send?access_token=test-token",
        "smile.dingtalk.secret=SEC-test-secret",
        "smile.dingtalk.initial-delay-millis=3600000",
        "smile.dingtalk.retry-base-seconds=30"
})
class DingTalkApprovalNotificationDispatcherTest {
    private static final AtomicLong TASK_SEQUENCE = new AtomicLong(900_000);

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ApprovalNotificationOutboxService outboxService;
    @Autowired DingTalkApprovalNotificationDispatcher dispatcher;
    @MockBean DingTalkRobotClient robotClient;

    @Test
    void claimedMessageIsSentAndAudited() {
        long approvalId = insertApproval("待审核候选人报告");
        outboxService.enqueueDingTalk(approvalId);

        assertThat(dispatcher.dispatchOnce()).isEqualTo(1);

        assertThat(outboxStatus(approvalId)).isEqualTo("SENT");
        Integer auditCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM ai_human_approval_action
                WHERE approval_id=? AND action_type='NOTIFY_DINGTALK_SENT'
                """, Integer.class, approvalId);
        assertThat(auditCount).isEqualTo(1);
        verify(robotClient).sendMarkdown(anyString(), anyString());
    }

    @Test
    void deliveryFailureMovesMessageToExponentialBackoffRetry() {
        long approvalId = insertApproval("待审核敏感查询");
        outboxService.enqueueDingTalk(approvalId);
        doThrow(new DingTalkDeliveryException("模拟网络故障"))
                .when(robotClient).sendMarkdown(anyString(), anyString());

        assertThat(dispatcher.dispatchOnce()).isZero();

        String status = outboxStatus(approvalId);
        Integer retryCount = jdbcTemplate.queryForObject("""
                SELECT retry_count FROM ai_approval_notification_outbox WHERE approval_id=?
                """, Integer.class, approvalId);
        assertThat(status).isEqualTo("RETRY");
        assertThat(retryCount).isEqualTo(1);
    }

    private long insertApproval(String title) {
        long taskId = TASK_SEQUENCE.incrementAndGet();
        jdbcTemplate.update("""
                INSERT INTO ai_human_approval(
                    run_id, task_id, node_code, approval_type, origin_type, risk_level,
                    reviewer_group, title, status, due_at)
                VALUES(1,?,'TEST_REVIEW','WORKFLOW','WORKFLOW_RUN','L1',
                       'HR_ADMIN',?,'PENDING',DATEADD('HOUR', 1, CURRENT_TIMESTAMP))
                """, taskId, title);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM ai_human_approval WHERE task_id=?", Long.class, taskId);
    }

    private String outboxStatus(long approvalId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM ai_approval_notification_outbox WHERE approval_id=?",
                String.class, approvalId);
    }
}
