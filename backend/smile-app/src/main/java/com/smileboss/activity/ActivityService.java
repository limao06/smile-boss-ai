package com.smileboss.activity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smileboss.ai.LlmGateway;
import com.smileboss.common.BizException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 根据最近站内行为生成运营活跃度；该分数不参与候选人能力或录用判断。 */
@Service
public class ActivityService {
    private static final int ANALYSIS_WINDOW_DAYS = 30;
    private static final int MAX_SCORE = 100;
    private static final Map<String, Integer> EVENT_WEIGHTS = Map.ofEntries(
            Map.entry("LOGIN", 1),
            Map.entry("PROFILE_UPDATED", 5),
            Map.entry("RESUME_UPDATED", 10),
            Map.entry("JOB_VIEWED", 1),
            Map.entry("JOB_FAVORITED", 3),
            Map.entry("JOB_APPLIED", 8),
            Map.entry("INTERVIEW_CONFIRMED", 10),
            Map.entry("MOCK_INTERVIEW_COMPLETED", 8),
            Map.entry("HR_REPLIED", 6));

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final LlmGateway llmGateway;

    public ActivityService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, LlmGateway llmGateway) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.llmGateway = llmGateway;
    }

    public void record(long candidateId, String eventType, String objectType,
                       Long objectId, Map<String, Object> metadata) {
        jdbcTemplate.update("""
                INSERT INTO candidate_activity_event(
                    candidate_id, event_type, object_type, object_id, metadata_json)
                VALUES(?,?,?,?,?)
                """, candidateId, eventType, objectType, objectId, toJson(metadata));
    }

    public Map<String, Object> analyze(long candidateId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.minusDays(ANALYSIS_WINDOW_DAYS);
        // 在数据库层裁剪时间范围，避免候选人历史事件越多，应用内存和网络开销越大。
        List<Map<String, Object>> events = jdbcTemplate.queryForList("""
                SELECT event_type, created_at
                FROM candidate_activity_event
                WHERE candidate_id=? AND created_at>=?
                ORDER BY created_at DESC
                """, candidateId, Timestamp.valueOf(windowStart));

        Map<String, Integer> eventCounts = new LinkedHashMap<>();
        double weightedScore = calculateWeightedScore(events, eventCounts, now);
        int score = Math.min(MAX_SCORE, (int) Math.round(weightedScore * 2));
        List<String> recommendedActions = recommendActions(events, eventCounts);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("candidateId", candidateId);
        result.put("score", score);
        result.put("level", activityLevel(score));
        result.put("eventCount", events.size());
        result.put("eventCounts", eventCounts);
        result.put("recommendedActions", recommendedActions);
        result.put("aiSummary", summarize(score, eventCounts, recommendedActions));
        return result;
    }

    private double calculateWeightedScore(List<Map<String, Object>> events,
                                          Map<String, Integer> eventCounts,
                                          LocalDateTime now) {
        double total = 0;
        for (Map<String, Object> event : events) {
            String eventType = String.valueOf(event.get("event_type"));
            eventCounts.merge(eventType, 1, Integer::sum);
            long ageInDays = Math.max(0, ChronoUnit.DAYS.between(toTime(event.get("created_at")), now));
            total += EVENT_WEIGHTS.getOrDefault(eventType, 1) * timeDecay(ageInDays);
        }
        return total;
    }

    private List<String> recommendActions(List<Map<String, Object>> events, Map<String, Integer> eventCounts) {
        List<String> actions = new ArrayList<>();
        if (eventCounts.getOrDefault("RESUME_UPDATED", 0) > 0) {
            actions.add("候选人近期更新了简历，建议及时跟进");
        }
        if (eventCounts.getOrDefault("JOB_VIEWED", 0) >= 5) {
            actions.add("候选人近期频繁浏览职位，可以推送相似岗位");
        }
        if (events.isEmpty()) {
            actions.add("候选人近 30 天无行为，可发送一次低频唤醒消息");
        }
        return actions;
    }

    private String summarize(int score, Map<String, Integer> eventCounts, List<String> actions) {
        String systemPrompt = "你是招聘运营分析助手。用一句中文客观总结候选人近30天站内求职活跃情况，"
                + "不推断隐私、人格或能力，不建议自动淘汰。";
        String userPrompt = "活跃分=" + score + "，事件统计=" + eventCounts + "，规则建议=" + actions;
        return llmGateway.chat(systemPrompt, userPrompt, "ACTIVITY_ANALYSIS")
                .map(String::trim)
                .orElse("基于近 30 天站内行为统计生成；活跃度仅用于跟进提醒，不代表岗位胜任力。");
    }

    private String toJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (JsonProcessingException exception) {
            throw new BizException("行为元数据序列化失败：" + exception.getOriginalMessage());
        }
    }

    private static double timeDecay(long ageInDays) {
        if (ageInDays <= 7) {
            return 1.0;
        }
        return ageInDays <= 14 ? 0.7 : 0.4;
    }

    private static String activityLevel(int score) {
        if (score >= 80) {
            return "HIGH";
        }
        if (score >= 50) {
            return "MEDIUM";
        }
        return score >= 20 ? "LOW" : "DORMANT";
    }

    private static LocalDateTime toTime(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        throw new BizException("行为事件时间格式错误");
    }
}
