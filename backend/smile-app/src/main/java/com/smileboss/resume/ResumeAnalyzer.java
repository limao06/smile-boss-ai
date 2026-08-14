package com.smileboss.resume;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smileboss.ai.LlmGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 简历结构化和完整度规则。模型只增强非身份字段，确定性规则始终保留。 */
@Component
public class ResumeAnalyzer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResumeAnalyzer.class);
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(1[3-9]\\d{9})(?!\\d)");
    private static final Pattern EMAIL =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern EXPERIENCE_YEARS =
            Pattern.compile("(\\d{1,2})\\s*年(?:工作|开发|从业)?经验");
    private static final Pattern TIMELINE = Pattern.compile("20\\d{2}[年./-]");
    private static final Pattern QUANTIFIED_RESULT =
            Pattern.compile("\\d+[%万亿]|QPS|TPS|P95|P99", Pattern.CASE_INSENSITIVE);
    private static final List<String> KNOWN_SKILLS = List.of(
            "Java", "Spring Boot", "Spring Cloud", "MySQL", "Redis", "Kafka", "Docker",
            "Kubernetes", "Python", "Vue", "React", "RAG", "大模型", "产品设计", "数据分析");

    private final ObjectMapper objectMapper;
    private final LlmGateway llmGateway;

    public ResumeAnalyzer(ObjectMapper objectMapper, LlmGateway llmGateway) {
        this.objectMapper = objectMapper;
        this.llmGateway = llmGateway;
    }

    public AnalysisResult analyze(Map<String, Object> candidate, String resumeText) {
        Map<String, Object> structured = structureByRules(candidate, resumeText);
        enrichWithModel(structured, maskDirectIdentifiers(resumeText));
        Map<String, Object> completeness = evaluateCompleteness(structured, resumeText);
        return new AnalysisResult(structured, completeness);
    }

    private Map<String, Object> structureByRules(Map<String, Object> candidate, String text) {
        List<String> skills = KNOWN_SKILLS.stream()
                .filter(skill -> containsIgnoreCase(text, skill))
                .toList();
        int yearsOfExperience = estimateYears(text, candidate.get("years_of_experience"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", candidate.getOrDefault("name", firstNonEmptyLine(text)));
        result.put("phone", find(text, PHONE));
        result.put("email", find(text, EMAIL));
        result.put("city", candidate.getOrDefault("city", ""));
        result.put("yearsOfExperience", yearsOfExperience);
        result.put("skills", skills);
        result.put("hasEducation", containsAny(text, "教育经历", "教育背景", "大学", "学院", "本科", "硕士", "博士"));
        result.put("hasWorkExperience", containsAny(text, "工作经历", "工作经验", "有限公司", "任职"));
        result.put("hasProjects", containsAny(text, "项目经历", "项目经验", "项目描述", "项目职责"));
        result.put("hasQuantifiedResults", QUANTIFIED_RESULT.matcher(text).find());
        result.put("summary", yearsOfExperience + " 年工作经验，技能包括 "
                + (skills.isEmpty() ? "待补充" : String.join("、", skills)));
        result.put("evidence", buildSkillEvidence(text, skills));
        result.put("parser", "RULE_FALLBACK");
        return result;
    }

    private void enrichWithModel(Map<String, Object> structured, String maskedText) {
        if (!llmGateway.isEnabled()) {
            return;
        }
        String systemPrompt = "你是招聘简历解析器。只输出JSON对象，不要Markdown。字段只能包含"
                + "yearsOfExperience、skills、summary；skills必须为字符串数组。不得编造简历中没有的信息。";
        llmGateway.chat(systemPrompt, "请结构化解析以下已脱敏简历，并保留可验证事实：\n" + maskedText, "RESUME_PARSE")
                .flatMap(this::parseJsonObject)
                .ifPresent(modelResult -> mergeModelResult(structured, modelResult));
    }

    private void mergeModelResult(Map<String, Object> structured, Map<String, Object> modelResult) {
        for (String key : List.of("yearsOfExperience", "summary")) {
            Object value = modelResult.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                structured.put(key, value);
            }
        }
        if (modelResult.get("skills") instanceof List<?> skills && !skills.isEmpty()) {
            structured.put("skills", skills.stream().map(String::valueOf).toList());
        }
        structured.put("parser", "LLM_WITH_RULE_FALLBACK");
    }

    @SuppressWarnings("unchecked")
    private Optional<Map<String, Object>> parseJsonObject(String rawResponse) {
        try {
            String json = rawResponse.trim()
                    .replaceFirst("^```(?:json)?\\s*", "")
                    .replaceFirst("\\s*```$", "");
            return Optional.of(objectMapper.readValue(json, LinkedHashMap.class));
        } catch (JsonProcessingException exception) {
            LOGGER.warn("Unable to parse resume model output; using rule result: {}",
                    exception.getOriginalMessage());
            return Optional.empty();
        }
    }

    private Map<String, Object> evaluateCompleteness(Map<String, Object> structured, String text) {
        CompletenessAccumulator accumulator = new CompletenessAccumulator();
        accumulator.check(!isBlank(structured.get("name")), 5,
                "姓名缺失", "补充真实姓名", "HIGH", null);
        accumulator.check(!isBlank(structured.get("phone")) || !isBlank(structured.get("email")), 5,
                "联系方式缺失", "至少补充手机号或邮箱", "HIGH", null);
        accumulator.check(booleanValue(structured.get("hasWorkExperience")), 20,
                "工作经历缺失", "补充公司、职位、时间和职责", "HIGH", "包含工作经历");
        accumulator.check(booleanValue(structured.get("hasProjects")), 20,
                "项目经历缺失", "补充项目背景、个人职责和结果", "HIGH", "包含项目经历");
        accumulator.check(!stringList(structured.get("skills")).isEmpty(), 15,
                "技能信息不足", "补充与目标岗位相关的技能", "MEDIUM", "技能信息清晰");
        accumulator.check(booleanValue(structured.get("hasEducation")), 10,
                "教育经历缺失", "补充学校、专业、学历及时间", "MEDIUM", null);
        accumulator.check(TIMELINE.matcher(text).find(), 10,
                "经历时间不完整", "为教育、工作和项目经历补充起止时间", "MEDIUM", null);
        accumulator.check(booleanValue(structured.get("hasQuantifiedResults")), 10,
                "缺少量化成果", "补充性能、规模、效率或业务结果", "MEDIUM", "包含量化成果");
        accumulator.check(text.length() >= 400, 5,
                "简历内容偏少", "补充职责、项目难点和个人贡献", "LOW", null);
        return accumulator.toResult();
    }

    private static Map<String, String> buildSkillEvidence(String text, List<String> skills) {
        Map<String, String> evidence = new LinkedHashMap<>();
        for (String skill : skills) {
            evidence.put(skill, evidenceAround(text, skill));
        }
        return evidence;
    }

    private static String maskDirectIdentifiers(String text) {
        String maskedPhone = PHONE.matcher(text).replaceAll("[PHONE]");
        return EMAIL.matcher(maskedPhone).replaceAll("[EMAIL]");
    }

    private static String find(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group() : "";
    }

    private static int estimateYears(String text, Object fallback) {
        Matcher matcher = EXPERIENCE_YEARS.matcher(text);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return fallback instanceof Number number ? number.intValue() : 0;
    }

    private static String evidenceAround(String text, String keyword) {
        String lowerText = text.toLowerCase(Locale.ROOT);
        int index = lowerText.indexOf(keyword.toLowerCase(Locale.ROOT));
        if (index < 0) {
            return "";
        }
        int start = Math.max(0, index - 45);
        int end = Math.min(text.length(), index + keyword.length() + 80);
        return text.substring(start, end).replace('\n', ' ');
    }

    private static String firstNonEmptyLine(String text) {
        return text.lines().map(String::trim).filter(line -> !line.isBlank()).findFirst().orElse("待确认");
    }

    private static boolean containsIgnoreCase(String text, String value) {
        return text.toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT));
    }

    private static boolean containsAny(String text, String... values) {
        return Arrays.stream(values).anyMatch(text::contains);
    }

    private static boolean isBlank(Object value) {
        return value == null || String.valueOf(value).isBlank();
    }

    private static boolean booleanValue(Object value) {
        return Boolean.TRUE.equals(value);
    }

    private static List<String> stringList(Object value) {
        return value instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }

    private static String completenessLevel(int score) {
        if (score >= 85) {
            return "EXCELLENT";
        }
        if (score >= 70) {
            return "GOOD";
        }
        return score >= 50 ? "NEEDS_IMPROVEMENT" : "INCOMPLETE";
    }

    public record AnalysisResult(Map<String, Object> structured, Map<String, Object> completeness) {}

    private static final class CompletenessAccumulator {
        private int score;
        private final List<Map<String, String>> issues = new ArrayList<>();
        private final List<String> strengths = new ArrayList<>();

        void check(boolean passed, int points, String issueTitle, String suggestion,
                   String severity, String strength) {
            if (passed) {
                score += points;
                if (strength != null) {
                    strengths.add(strength);
                }
                return;
            }
            issues.add(Map.of("title", issueTitle, "suggestion", suggestion, "severity", severity));
        }

        Map<String, Object> toResult() {
            int boundedScore = Math.min(100, score);
            return Map.of(
                    "score", boundedScore,
                    "level", completenessLevel(boundedScore),
                    "issues", List.copyOf(issues),
                    "strengths", List.copyOf(strengths));
        }
    }
}
