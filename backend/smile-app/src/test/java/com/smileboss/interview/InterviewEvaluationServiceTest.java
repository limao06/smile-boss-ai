package com.smileboss.interview;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterviewEvaluationServiceTest {
    private final InterviewEvaluationService evaluationService = new InterviewEvaluationService();

    @Test
    void quantifiedStarAnswerGetsExplainableStrengths() {
        String answer = "项目发生性能问题后，我负责定位慢查询，先补充监控，再设计索引并实现缓存降级。"
                + "最终接口耗时降低60%，随后我组织了复盘并补齐压测门禁。";

        int score = evaluationService.score(answer);
        Map<String, Object> evaluation = evaluationService.buildPracticeEvaluation(answer, score);

        assertTrue(score >= 70);
        @SuppressWarnings("unchecked")
        List<String> strengths = (List<String>) evaluation.get("strengths");
        assertTrue(strengths.contains("包含量化信息"));
        assertTrue(strengths.contains("能够说明个人贡献"));
    }

    @Test
    void blankAnswerGetsMinimumPracticeScore() {
        assertEquals(20, evaluationService.score("  "));
        assertEquals("WEAK", evaluationService.level(20));
    }
}
