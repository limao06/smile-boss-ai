package com.smileboss.sqlai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smileboss.ai.LlmGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 负责把招聘领域问题转换成候选 SQL，不参与校验和执行。
 *
 * <p>优先使用项目现有模型网关。模型关闭、不可用或返回格式错误时，使用确定性模板降级，
 * 确保本地开发和核心指标查询不依赖外部服务。</p>
 */
@Component
public class TextToSqlGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger(TextToSqlGenerator.class);
    private static final DateTimeFormatter SQL_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String MODEL_SCENE = "TEXT_TO_SQL_GENERATION";
    private static final String MODEL_SYSTEM_PROMPT = """
            你是招聘分析 Text-to-SQL Agent。只生成一条 MySQL SELECT 或 WITH 查询，不得生成任何写操作。
            只可使用表：recruit_job、talent_candidate、talent_resume、recruit_recommendation、
            mock_interview_session、ai_interview_invitation、ai_interview_session、candidate_activity_event。
            表关系：推荐表通过 candidate_id/job_id 连接候选人和职位；面试会话同样通过 candidate_id/job_id 连接。
            联系方式、姓名、简历原文、面试原回答属于敏感信息。只返回JSON：
            {"sql":"...","rationale":"...","intent":{"metric":"...","dimensions":[],"filters":[]}}
            """;

    private final LlmGateway llmGateway;
    private final ObjectMapper objectMapper;

    public TextToSqlGenerator(LlmGateway llmGateway, ObjectMapper objectMapper) {
        this.llmGateway = llmGateway;
        this.objectMapper = objectMapper;
    }

    public SqlDraft generate(String question) {
        SqlDraft fallbackDraft = generateByRules(question);
        if (!llmGateway.isEnabled()) {
            return fallbackDraft;
        }

        Optional<String> response = llmGateway.chat(MODEL_SYSTEM_PROMPT, question, MODEL_SCENE);
        if (response.isEmpty()) {
            return fallbackDraft;
        }
        return parseModelResponse(response.get()).orElse(fallbackDraft);
    }

    private Optional<SqlDraft> parseModelResponse(String response) {
        String json = stripMarkdownFence(response);
        try {
            JsonNode root = objectMapper.readTree(json);
            String sql = root.path("sql").asText().trim();
            if (sql.isEmpty()) {
                LOGGER.warn("Text-to-SQL model returned JSON without sql field; using rule fallback");
                return Optional.empty();
            }
            return Optional.of(new SqlDraft(
                    sql,
                    root.path("rationale").asText("由配置模型生成"),
                    jsonObjectToMap(root.path("intent")),
                    "LLM"));
        } catch (JsonProcessingException exception) {
            // 模型输出属于不可信外部输入。记录失败原因，但不记录完整响应，避免敏感信息进入日志。
            LOGGER.warn("Unable to parse Text-to-SQL model output; using rule fallback: {}",
                    exception.getOriginalMessage());
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> jsonObjectToMap(JsonNode node) {
        if (!node.isObject()) {
            return Map.of();
        }
        return objectMapper.convertValue(node, Map.class);
    }

    private SqlDraft generateByRules(String question) {
        String normalizedQuestion = normalizeQuestion(question);
        Map<String, Object> intent = new LinkedHashMap<>();
        String sql;
        String rationale;

        if (containsAny(normalizedQuestion, "手机号", "电话", "邮箱", "联系方式", "phone", "email", "联系人")) {
            sql = "SELECT id, name, phone, email, desired_position "
                    + "FROM talent_candidate ORDER BY id DESC";
            intent.put("entity", "candidate_contact");
            rationale = "问题要求候选人联系方式，属于个人敏感信息，执行前必须人工审核";
        } else if (containsAny(normalizedQuestion, "城市", "地区")
                && containsAny(normalizedQuestion, "候选", "人才")) {
            sql = "SELECT city, COUNT(*) AS candidate_count "
                    + "FROM talent_candidate GROUP BY city ORDER BY candidate_count DESC";
            intent.put("metric", "candidate_count");
            intent.put("dimensions", List.of("city"));
            rationale = "按候选人所在城市聚合人才库规模";
        } else if (containsAny(normalizedQuestion, "活跃", "行为")
                && containsAny(normalizedQuestion, "30", "三十", "最近")) {
            String since = LocalDateTime.now().minusDays(30).format(SQL_TIMESTAMP_FORMATTER);
            sql = "SELECT candidate_id, COUNT(*) AS event_count, MAX(created_at) AS last_active_at "
                    + "FROM candidate_activity_event WHERE created_at >= '" + since + "' "
                    + "GROUP BY candidate_id ORDER BY event_count DESC";
            intent.put("metric", "activity_event_count");
            intent.put("windowDays", 30);
            rationale = "统计最近30天候选人有效行为次数和最近活跃时间";
        } else if (containsAny(normalizedQuestion, "推荐", "匹配")
                && containsAny(normalizedQuestion, "平均", "职位", "岗位", "分")) {
            sql = "SELECT j.title AS job_title, COUNT(*) AS recommendation_count, "
                    + "ROUND(AVG(r.score), 2) AS average_score "
                    + "FROM recruit_recommendation r JOIN recruit_job j ON j.id=r.job_id "
                    + "GROUP BY j.id, j.title ORDER BY average_score DESC";
            intent.put("metric", "recommendation_average_score");
            intent.put("dimensions", List.of("job"));
            rationale = "按职位汇总推荐数量和平均匹配分";
        } else if (containsAny(normalizedQuestion, "面试")
                && containsAny(normalizedQuestion, "职位", "岗位", "完成", "数量")) {
            sql = "SELECT j.title AS job_title, COUNT(*) AS interview_count, "
                    + "ROUND(AVG(s.ai_score), 2) AS average_ai_score "
                    + "FROM ai_interview_session s JOIN recruit_job j ON j.id=s.job_id "
                    + "WHERE s.status='COMPLETED' GROUP BY j.id, j.title ORDER BY interview_count DESC";
            intent.put("metric", "completed_interview_count");
            intent.put("dimensions", List.of("job"));
            rationale = "按职位汇总已完成正式AI面试数量及平均分";
        } else if (containsAny(normalizedQuestion, "职位", "岗位", "在招")
                && containsAny(normalizedQuestion, "多少", "数量", "总数", "count")) {
            sql = "SELECT status, COUNT(*) AS job_count "
                    + "FROM recruit_job GROUP BY status ORDER BY job_count DESC";
            intent.put("metric", "job_count");
            intent.put("dimensions", List.of("status"));
            rationale = "按发布状态统计职位数量";
        } else if (containsAny(normalizedQuestion, "候选", "人才")
                && containsAny(normalizedQuestion, "多少", "数量", "总数", "count")) {
            sql = "SELECT COUNT(*) AS candidate_count FROM talent_candidate";
            intent.put("metric", "candidate_count");
            rationale = "统计人才库候选人总量";
        } else {
            sql = "SELECT "
                    + "(SELECT COUNT(*) FROM recruit_job WHERE status='PUBLISHED') AS published_job_count, "
                    + "(SELECT COUNT(*) FROM talent_candidate) AS candidate_count, "
                    + "(SELECT COUNT(*) FROM ai_interview_invitation) AS interview_invitation_count";
            intent.put("metric", "recruitment_overview");
            rationale = "未命中特定指标时返回最小化的招聘经营概览，不返回个人明细";
        }
        return new SqlDraft(sql, rationale, Map.copyOf(intent), "RULE_FALLBACK");
    }

    private static String stripMarkdownFence(String response) {
        return response.trim()
                .replaceFirst("(?s)^```(?:json)?\\s*", "")
                .replaceFirst("(?s)\\s*```$", "");
    }

    private static String normalizeQuestion(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static boolean containsAny(String value, String... keywords) {
        return Arrays.stream(keywords).anyMatch(value::contains);
    }

    public record SqlDraft(String sql, String rationale, Map<String, Object> intent, String generator) {}
}
