package com.smileboss;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ResumeWorkspaceIntegrationTest {
    private static final long CANDIDATE_ID = 99L;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void prepareCandidate() {
        jdbcTemplate.update("DELETE FROM resume_greeting_generation WHERE candidate_id=?", CANDIDATE_ID);
        jdbcTemplate.update("DELETE FROM resume_optimization_task WHERE candidate_id=?", CANDIDATE_ID);
        List<Map<String, Object>> workspaces = jdbcTemplate.queryForList(
                "SELECT id FROM candidate_resume_workspace WHERE candidate_id=?", CANDIDATE_ID);
        for (Map<String, Object> workspace : workspaces) {
            jdbcTemplate.update("DELETE FROM candidate_resume_version WHERE workspace_id=?", workspace.get("id"));
        }
        jdbcTemplate.update("DELETE FROM candidate_resume_workspace WHERE candidate_id=?", CANDIDATE_ID);
        jdbcTemplate.update("DELETE FROM talent_resume WHERE candidate_id=?", CANDIDATE_ID);
        jdbcTemplate.update("DELETE FROM talent_candidate WHERE id=?", CANDIDATE_ID);
        jdbcTemplate.update("""
                INSERT INTO talent_candidate(
                    id,user_id,name,phone,email,city,years_of_experience,desired_position,skills,profile_summary)
                VALUES(99,99,'测试候选人','13900000000','resume-test@example.com','上海',5,
                       'Java 高级开发工程师','Java,Spring Boot,MySQL,Redis','5 年 Java 后端开发经验')
                """);
    }

    @Test
    void completeResumeCopilotFlowIsVersionedAndEvidenceBounded() throws Exception {
        String adminAuth = login("admin", "admin123");

        mvc.perform(get("/api/candidates/99/resume-workspace").header("Authorization", adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentVersion.versionNo").value(1));

        Map<String, Object> content = Map.ofEntries(
                Map.entry("name", "测试候选人"),
                Map.entry("phone", "13900000000"),
                Map.entry("email", "resume-test@example.com"),
                Map.entry("city", "上海"),
                Map.entry("desiredPosition", "Java 高级开发工程师"),
                Map.entry("yearsOfExperience", 5),
                Map.entry("summary", "5 年 Java 后端开发经验"),
                Map.entry("skills", List.of("Java", "Spring Boot", "MySQL", "Redis")),
                Map.entry("workExperience", "2021-2026 某科技公司 Java 开发工程师，负责核心服务开发。"),
                Map.entry("projectExperience", "招聘平台项目：负责候选人服务，接口性能提升 40%。"),
                Map.entry("education", "2017-2021 某大学 计算机科学与技术 本科"),
                Map.entry("certificates", ""),
                Map.entry("sourceText", "工作经历 2021-2026；项目经历 招聘平台，接口性能提升40%；教育经历 某大学本科。")
        );
        mvc.perform(put("/api/candidates/99/resume-workspace").header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", content, "changeSummary", "测试在线编辑"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentVersion.versionNo").value(2));

        String importResponse = mvc.perform(post("/api/resumes/parse-text").header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("candidateId", CANDIDATE_ID,
                                "text", "测试候选人 13900000000 工作经历 5年Java经验；项目经历 Spring Boot招聘平台；教育经历 某大学本科。"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workspaceImport.requiresConfirmation").value(true))
                .andExpect(jsonPath("$.data.workspaceImport.publishStatus").value("AWAITING_CONFIRMATION"))
                .andReturn().getResponse().getContentAsString();
        long importedVersionId = mapper.readTree(importResponse).path("data")
                .path("workspaceImport").path("versionId").asLong();

        mvc.perform(post("/api/candidates/99/resume-workspace/versions/{id}/publish", importedVersionId)
                        .header("Authorization", adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentVersionId").value(importedVersionId));

        String optimizationResponse = mvc.perform(post("/api/candidates/99/resume-workspace/optimize")
                        .header("Authorization", adminAuth).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("targetJobId", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.suggestions").isArray())
                .andReturn().getResponse().getContentAsString();
        JsonNode optimization = mapper.readTree(optimizationResponse).path("data");
        long taskId = optimization.path("id").asLong();

        mvc.perform(post("/api/candidates/99/resume-workspace/optimizations/{id}/apply", taskId)
                        .header("Authorization", adminAuth).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("suggestionIds", List.of("SUMMARY_REWRITE")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPLIED"));

        String generatedResponse = mvc.perform(post("/api/candidates/99/resume-workspace/generate")
                        .header("Authorization", adminAuth).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("targetJobId", 1, "templateCode", "ATS_STANDARD_V1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionType").value("TARGETED"))
                .andReturn().getResponse().getContentAsString();
        long generatedVersionId = mapper.readTree(generatedResponse).path("data").path("id").asLong();

        mvc.perform(get("/api/candidates/99/resume-workspace/versions/{id}/export", generatedVersionId)
                        .header("Authorization", adminAuth))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("测试候选人")));

        mvc.perform(post("/api/candidates/99/resume-workspace/greetings")
                        .header("Authorization", adminAuth).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("jobId", 1, "tone", "PROFESSIONAL", "maximumCharacters", 120))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.variants[0].content").isNotEmpty())
                .andExpect(jsonPath("$.data.evidence").isArray());
    }

    @Test
    void candidateCannotReadAnotherCandidatesWorkspace() throws Exception {
        String candidateAuth = login("candidate", "demo123");
        mvc.perform(get("/api/candidates/99/resume-workspace").header("Authorization", candidateAuth))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("无权访问该候选人的简历"));
    }

    private String login(String username, String password) throws Exception {
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + mapper.readTree(response).path("data").path("token").asText();
    }

    private String json(Object value) throws Exception {
        return mapper.writeValueAsString(value);
    }
}
