package com.smileboss;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AgentPlatformIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void candidateReportWorkflowPausesForHumanAndResumesToCompletion() throws Exception {
        String auth = "Bearer " + login("admin", "admin123");

        mvc.perform(get("/api/agent-platform/agents").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.agent_code == 'RESUME_RESEARCHER')]").exists());

        mvc.perform(get("/api/agent-platform/workflows/CANDIDATE_REPORT").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.nodes.length()").value(8));

        String started = mvc.perform(post("/api/agent-platform/runs").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "workflowCode", "CANDIDATE_REPORT",
                                "businessType", "CANDIDATE_JOB",
                                "businessId", "1:1",
                                "input", Map.of(
                                        "candidateId", 1,
                                        "jobId", 1,
                                        "goal", "生成候选人与 Java 高级开发岗位的证据化综合报告")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WAITING_HUMAN"))
                .andExpect(jsonPath("$.data.humanApprovals[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.artifacts.length()").value(6))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode startedData = mapper.readTree(started).path("data");
        long runId = startedData.path("id").asLong();
        long taskId = startedData.path("humanApprovals").path(0).path("task_id").asLong();

        mvc.perform(get("/api/agent-platform/runs/" + runId).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events[?(@.event_type == 'HUMAN_APPROVAL_REQUESTED')]").exists());

        mvc.perform(post("/api/agent-platform/human-tasks/" + taskId + "/decisions")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("decision", "APPROVE", "comment", "证据链和审计结果已人工核对"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.humanApprovals[0].decision").value("APPROVE"))
                .andExpect(jsonPath("$.data.artifacts[?(@.artifact_type == 'CANDIDATE_REPORT_FINAL')]").exists())
                .andExpect(jsonPath("$.data.events[?(@.event_type == 'RUN_COMPLETED')]").exists());
    }

    @Test
    void draftAgentAndWorkflowCanBeCreatedAndPublished() throws Exception {
        String auth = "Bearer " + login("admin", "admin123");
        mvc.perform(post("/api/agent-platform/agents").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "code", "DEMO_SUMMARIZER",
                                "name", "演示摘要 Agent",
                                "systemPrompt", "只返回包含 summary 字段的 JSON 对象",
                                "modelPolicy", "STRUCTURED_EXTRACTION",
                                "outputSchema", Map.of("type", "object", "required", new String[]{"summary"})))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        mvc.perform(post("/api/agent-platform/agents/DEMO_SUMMARIZER/versions/1/publish").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        Map<String, Object> workflow = Map.of(
                "code", "DEMO_SUMMARY_FLOW",
                "name", "演示摘要工作流",
                "inputSchema", Map.of("type", "object", "required", new String[]{"text"}),
                "budget", Map.of("maxNodeExecutions", 10, "maxDurationSeconds", 120),
                "nodes", new Object[]{
                        Map.of("code", "START", "type", "START", "name", "开始"),
                        Map.of("code", "SUMMARIZE", "type", "AGENT", "name", "摘要", "config", Map.of(
                                "agentCode", "DEMO_SUMMARIZER", "artifactType", "DEMO_SUMMARY")),
                        Map.of("code", "END", "type", "END", "name", "结束")
                },
                "edges", new Object[]{
                        Map.of("from", "START", "to", "SUMMARIZE"),
                        Map.of("from", "SUMMARIZE", "to", "END")
                });
        mvc.perform(post("/api/agent-platform/workflows").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(json(workflow)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
        mvc.perform(post("/api/agent-platform/workflows/DEMO_SUMMARY_FLOW/versions/1/publish").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        mvc.perform(post("/api/agent-platform/runs").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("workflowCode", "DEMO_SUMMARY_FLOW", "input", Map.of("text", "需要生成摘要的内容")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.artifacts[?(@.artifact_type == 'DEMO_SUMMARY')]").exists());
    }

    private String login(String username, String password) throws Exception {
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return mapper.readTree(response).path("data").path("token").asText();
    }

    private String json(Object value) throws Exception { return mapper.writeValueAsString(value); }
}
