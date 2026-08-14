package com.smileboss;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TextToSqlIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void aggregateQueryIsValidatedAndExecutedAutomatically() throws Exception {
        String auth = "Bearer " + login();
        mvc.perform(post("/api/text-to-sql/queries").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("question", "每个城市有多少候选人？", "execute", true, "maxRows", 100))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.risk_level").value("L0"))
                .andExpect(jsonPath("$.data.validations[?(@.validator_code == 'STATIC_SQL_GUARD')].passed").value(1))
                .andExpect(jsonPath("$.data.executions[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.executions[0].result_json[0].candidate_count").exists());
    }

    @Test
    void sensitiveQueryPausesForHumanAndExecutesOnlyAfterApproval() throws Exception {
        String auth = "Bearer " + login();
        String response = mvc.perform(post("/api/text-to-sql/queries").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("question", "列出候选人的电话和邮箱", "execute", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WAITING_HUMAN"))
                .andExpect(jsonPath("$.data.risk_level").value("L2"))
                .andExpect(jsonPath("$.data.approval.approval_type").value("TEXT_TO_SQL"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode responseData = mapper.readTree(response).path("data");
        long runId = responseData.path("id").asLong();
        long approvalId = responseData.path("approval").path("id").asLong();

        Integer queuedNotifications = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM ai_approval_notification_outbox
                WHERE approval_id=? AND channel='DINGTALK' AND status='PENDING'
                """, Integer.class, approvalId);
        org.assertj.core.api.Assertions.assertThat(queuedNotifications).isEqualTo(1);

        // 先认领再审核，覆盖租约与 decision_version 乐观锁链路。
        mvc.perform(post("/api/agent-platform/human-tasks/" + approvalId + "/claim")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("leaseMinutes", 30))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assigned_to").value(1))
                .andExpect(jsonPath("$.data.decision_version").value(1));

        mvc.perform(post("/api/text-to-sql/queries/" + runId + "/decisions").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("decision", "APPROVE", "comment", "已确认用于联系候选人"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.approval.decision").value("APPROVE"))
                .andExpect(jsonPath("$.data.executions[0].result_json[0].phone").exists());

        String notificationStatus = jdbcTemplate.queryForObject("""
                SELECT status FROM ai_approval_notification_outbox WHERE approval_id=?
                """, String.class, approvalId);
        org.assertj.core.api.Assertions.assertThat(notificationStatus).isEqualTo("CANCELLED");
    }

    private String login() throws Exception {
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "admin", "password", "admin123"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode data = mapper.readTree(response).path("data");
        return data.path("token").asText();
    }

    private String json(Object value) throws Exception { return mapper.writeValueAsString(value); }
}
