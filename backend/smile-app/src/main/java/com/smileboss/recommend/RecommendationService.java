package com.smileboss.recommend;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smileboss.ai.LlmGateway;
import com.smileboss.common.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 岗位推荐服务。确定性规则负责分数，LLM 只能补充解释和待核实问题，不能修改分数。
 */
@Service
public class RecommendationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecommendationService.class);
    private static final int MAX_AI_EXPLANATION_JOBS = 10;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final LlmGateway llmGateway;

    public RecommendationService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, LlmGateway llmGateway) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.llmGateway = llmGateway;
    }

    public List<Map<String, Object>> recommend(long candidateId) {
        // 联系方式不参与推荐，也不得发送给外部模型。
        Map<String, Object> candidate = findCandidateForRecommendation(candidateId);
        Set<String> candidateSkills = splitSkills(candidate.get("skills"));
        List<Map<String, Object>> recommendations = new ArrayList<>();

        List<Map<String, Object>> publishedJobs = jdbcTemplate.queryForList(
                "SELECT * FROM recruit_job WHERE status='PUBLISHED'");
        for (Map<String, Object> job : publishedJobs) {
            Map<String, Object> recommendation = evaluateJob(candidate, candidateSkills, job);
            saveRecommendation(candidateId, longValue(job.get("id")), recommendation);
            recommendations.add(recommendation);
        }

        recommendations.sort(Comparator.comparingInt(RecommendationService::scoreOf).reversed());
        addAiExplanations(candidate, recommendations);
        return recommendations;
    }

    private Map<String, Object> evaluateJob(Map<String, Object> candidate,
                                            Set<String> candidateSkills,
                                            Map<String, Object> job) {
        Set<String> requiredSkills = splitSkills(job.get("required_skills"));
        Set<String> matchedSkills = intersection(requiredSkills, candidateSkills);
        Set<String> missingSkills = difference(requiredSkills, candidateSkills);
        int candidateYears = intValue(candidate.get("years_of_experience"));
        int requiredYears = intValue(job.get("experience_years"));

        int skillScore = requiredSkills.isEmpty()
                ? 30 : (int) Math.round(30.0 * matchedSkills.size() / requiredSkills.size());
        int experienceScore = requiredYears == 0
                ? 20 : Math.min(20, (int) Math.round(20.0 * candidateYears / requiredYears));
        int intentScore = calculateIntentScore(
                stringValue(candidate.get("desired_position")), stringValue(job.get("title")));
        int cityScore = calculateCityScore(
                stringValue(candidate.get("city")), stringValue(job.get("city")));
        int evidenceBonus = matchedSkills.isEmpty() ? 0 : 10;
        int score = Math.min(100, skillScore + experienceScore + intentScore + cityScore + 15 + evidenceBonus);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jobId", job.get("id"));
        result.put("jobTitle", job.get("title"));
        result.put("city", job.get("city"));
        result.put("score", score);
        result.put("level", matchLevel(score));
        result.put("matchedSkills", matchedSkills);
        result.put("missingSkills", missingSkills);
        result.put("reason", "技能匹配 " + matchedSkills.size() + "/" + requiredSkills.size()
                + "，工作经验 " + candidateYears + " 年，岗位要求 " + requiredYears + " 年");
        return result;
    }

    private void addAiExplanations(Map<String, Object> candidate, List<Map<String, Object>> recommendations) {
        if (!llmGateway.isEnabled() || recommendations.isEmpty()) {
            return;
        }
        String systemPrompt = "你是人岗匹配解释器。规则分数不可修改。只输出JSON数组，每项只含jobId、"
                + "aiReason、verificationQuestions。不得依据性别、年龄、婚育、民族等敏感属性，"
                + "不得自动淘汰候选人。";
        String userPrompt = "候选人=" + candidate + "；规则匹配结果="
                + recommendations.stream().limit(MAX_AI_EXPLANATION_JOBS).toList()
                + "。请为每项提供简短、可核验的推荐解释和最多2个待核实问题。";
        llmGateway.chat(systemPrompt, userPrompt, "JOB_RECOMMEND_EXPLAIN")
                .ifPresent(response -> mergeAiExplanations(response, recommendations));
    }

    private void mergeAiExplanations(String response, List<Map<String, Object>> recommendations) {
        try {
            String json = stripMarkdownFence(response);
            List<?> items = objectMapper.readValue(json, List.class);
            for (Object item : items) {
                if (item instanceof Map<?, ?> explanation) {
                    mergeExplanation(explanation, recommendations);
                }
            }
        } catch (JsonProcessingException | NumberFormatException exception) {
            // AI 解释是可选增强。解析失败不影响确定性推荐结果，但必须留下可观测日志。
            LOGGER.warn("Unable to parse job recommendation explanation: {}", exception.getMessage());
        }
    }

    private void mergeExplanation(Map<?, ?> explanation, List<Map<String, Object>> recommendations) {
        long jobId = Long.parseLong(String.valueOf(explanation.get("jobId")));
        recommendations.stream()
                .filter(recommendation -> longValue(recommendation.get("jobId")) == jobId)
                .findFirst()
                .ifPresent(recommendation -> {
                    putIfPresent(recommendation, "aiReason", explanation.get("aiReason"));
                    putIfPresent(recommendation, "verificationQuestions", explanation.get("verificationQuestions"));
                });
    }

    private void saveRecommendation(long candidateId, long jobId, Map<String, Object> recommendation) {
        String resultJson;
        try {
            resultJson = objectMapper.writeValueAsString(recommendation);
        } catch (JsonProcessingException exception) {
            throw new BizException("推荐结果序列化失败：" + exception.getOriginalMessage());
        }

        int updatedRows = jdbcTemplate.update("""
                UPDATE recruit_recommendation
                SET score=?, result_json=?, created_at=CURRENT_TIMESTAMP
                WHERE candidate_id=? AND job_id=?
                """, scoreOf(recommendation), resultJson, candidateId, jobId);
        if (updatedRows == 0) {
            jdbcTemplate.update("""
                    INSERT INTO recruit_recommendation(candidate_id, job_id, score, result_json)
                    VALUES(?,?,?,?)
                    """, candidateId, jobId, scoreOf(recommendation), resultJson);
        }
    }

    private Map<String, Object> findCandidateForRecommendation(long candidateId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, city, years_of_experience, desired_position, skills, profile_summary
                FROM talent_candidate
                WHERE id=?
                """, candidateId);
        if (rows.isEmpty()) {
            throw new BizException("候选人不存在");
        }
        return rows.get(0);
    }

    private static Set<String> splitSkills(Object value) {
        Set<String> skills = new LinkedHashSet<>();
        if (value == null) {
            return skills;
        }
        for (String skill : String.valueOf(value).split("[,，、;；\\s]+")) {
            if (!skill.isBlank()) {
                skills.add(skill.trim().toLowerCase(Locale.ROOT));
            }
        }
        return skills;
    }

    private static Set<String> intersection(Set<String> left, Set<String> right) {
        Set<String> result = new LinkedHashSet<>(left);
        result.retainAll(right);
        return result;
    }

    private static Set<String> difference(Set<String> left, Set<String> right) {
        Set<String> result = new LinkedHashSet<>(left);
        result.removeAll(right);
        return result;
    }

    private static int calculateIntentScore(String desiredPosition, String jobTitle) {
        if (desiredPosition.isBlank()) {
            return 5;
        }
        return containsIgnoreCase(desiredPosition, jobTitle) || containsIgnoreCase(jobTitle, desiredPosition) ? 15 : 5;
    }

    private static int calculateCityScore(String candidateCity, String jobCity) {
        if (candidateCity.isBlank() || jobCity.isBlank()) {
            return 5;
        }
        return candidateCity.equalsIgnoreCase(jobCity) ? 10 : 0;
    }

    private static String matchLevel(int score) {
        if (score >= 85) {
            return "HIGH";
        }
        return score >= 70 ? "MEDIUM" : "LOW";
    }

    private static int scoreOf(Map<String, Object> recommendation) {
        return intValue(recommendation.get("score"));
    }

    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private static String stripMarkdownFence(String value) {
        return value.trim()
                .replaceFirst("^```(?:json)?\\s*", "")
                .replaceFirst("\\s*```$", "");
    }

    private static boolean containsIgnoreCase(String value, String fragment) {
        return !value.isBlank() && !fragment.isBlank()
                && value.toLowerCase(Locale.ROOT).contains(fragment.toLowerCase(Locale.ROOT));
    }

    private static int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
