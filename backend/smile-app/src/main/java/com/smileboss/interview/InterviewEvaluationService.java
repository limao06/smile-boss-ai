package com.smileboss.interview;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 面试回答的可解释规则评分器。
 *
 * <p>评分规则与会话持久化分离后，可以独立单元测试、调整权重，未来也可以无缝替换为
 * “规则分 + 大模型分 + 人工校准分”的组合评分器。</p>
 */
@Service
public class InterviewEvaluationService {
    private static final int MINIMUM_SCORE = 0;
    private static final int MAXIMUM_SCORE = 100;
    private static final Pattern QUANTIFIED_RESULT_PATTERN = Pattern.compile("(?s).*\\d+[%万亿秒毫].*");

    public int score(String answer) {
        if (answer == null || answer.isBlank()) {
            return 20;
        }

        String normalizedAnswer = answer.trim();
        int score = 40;
        if (normalizedAnswer.length() >= 80) {
            score += 15;
        }
        if (normalizedAnswer.length() >= 180) {
            score += 10;
        }
        if (hasQuantifiedResult(normalizedAnswer)) {
            score += 10;
        }
        if (containsAny(normalizedAnswer, "我负责", "我设计", "我实现", "我的职责")) {
            score += 10;
        }
        if (containsAny(normalizedAnswer, "结果", "提升", "降低", "最终", "复盘")) {
            score += 10;
        }
        if (normalizedAnswer.length() < 50 && containsAny(normalizedAnswer, "不知道", "不了解", "没做过")) {
            score -= 15;
        }
        return Math.max(MINIMUM_SCORE, Math.min(MAXIMUM_SCORE, score));
    }

    public Map<String, Object> buildPracticeEvaluation(String answer, int score) {
        String normalizedAnswer = answer == null ? "" : answer.trim();
        List<String> strengths = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        if (normalizedAnswer.length() >= 120) {
            strengths.add("回答信息较充分");
        } else {
            suggestions.add("增加背景、行动和结果，让回答更完整");
        }
        if (hasQuantifiedResult(normalizedAnswer)) {
            strengths.add("包含量化信息");
        } else {
            suggestions.add("补充规模、性能或业务结果等量化信息");
        }
        if (containsAny(normalizedAnswer, "我负责", "我设计", "我实现")) {
            strengths.add("能够说明个人贡献");
        } else {
            suggestions.add("明确区分团队工作和个人贡献");
        }

        return Map.of(
                "score", score,
                "strengths", strengths,
                "suggestions", suggestions,
                "notice", "评分仅用于练习反馈"
        );
    }

    public String level(int score) {
        if (score >= 85) {
            return "EXCELLENT";
        }
        if (score >= 70) {
            return "GOOD";
        }
        if (score >= 50) {
            return "NEEDS_IMPROVEMENT";
        }
        return "WEAK";
    }

    private boolean hasQuantifiedResult(String answer) {
        return QUANTIFIED_RESULT_PATTERN.matcher(answer).matches();
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
