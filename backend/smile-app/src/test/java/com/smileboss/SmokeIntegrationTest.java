package com.smileboss;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SmokeIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void loginJobsResumeAndRecommendationAreReachable() throws Exception {
        String login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "admin", "password", "admin123"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        String token = mapper.readTree(login).path("data").path("token").asText();
        String auth = "Bearer " + token;

        mvc.perform(get("/api/jobs").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].title").exists());

        mvc.perform(post("/api/resumes/parse-text").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("candidateId", 1, "text", "张三 13800138000 5年工作经验 教育经历 某大学本科。工作经历 某公司。项目经历 我负责Java Spring Boot MySQL Redis订单系统，将性能提升40%。"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.completeness.score").isNumber());

        mvc.perform(post("/api/ai/candidates/1/recommend-jobs").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].score").isNumber());
    }

    private String json(Object value) throws Exception { return mapper.writeValueAsString(value); }
}

