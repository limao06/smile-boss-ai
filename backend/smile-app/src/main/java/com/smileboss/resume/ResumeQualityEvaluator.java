package com.smileboss.resume;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** 确定性简历质量评分；模型不能修改分数，保证同一内容得到稳定结果。 */
public final class ResumeQualityEvaluator {
    private static final Pattern QUANTIFIED_RESULT =
            Pattern.compile("\\d+[%万亿]|QPS|TPS|P95|P99", Pattern.CASE_INSENSITIVE);

    private ResumeQualityEvaluator() {
    }

    public static Map<String, Object> evaluate(ResumeContent content) {
        int completeness = score(
                present(content.name()), 10,
                present(content.phone()) || present(content.email()), 10,
                present(content.summary()), 15,
                !content.skills().isEmpty(), 15,
                present(content.workExperience()), 20,
                present(content.projectExperience()), 20,
                present(content.education()), 10);
        int evidence = Math.min(100,
                (present(content.workExperience()) ? 35 : 0)
                        + (present(content.projectExperience()) ? 35 : 0)
                        + (QUANTIFIED_RESULT.matcher(content.sourceText()).find() ? 30 : 0));
        int clarity = Math.min(100,
                (content.summary().length() >= 30 ? 40 : 20)
                        + (!content.skills().isEmpty() ? 30 : 0)
                        + (content.sourceText().length() >= 400 ? 30 : 10));
        int atsReadability = 70
                + (present(content.email()) ? 10 : 0)
                + (present(content.workExperience()) ? 10 : 0)
                + (present(content.education()) ? 10 : 0);
        int overall = (int) Math.round(completeness * 0.40 + evidence * 0.25
                + clarity * 0.20 + atsReadability * 0.15);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("overall", overall);
        result.put("completeness", completeness);
        result.put("evidence", evidence);
        result.put("clarity", clarity);
        result.put("atsReadability", Math.min(100, atsReadability));
        result.put("level", overall >= 85 ? "EXCELLENT" : overall >= 70 ? "GOOD" : "NEEDS_IMPROVEMENT");
        return result;
    }

    private static int score(Object... checksAndPoints) {
        int result = 0;
        for (int index = 0; index < checksAndPoints.length; index += 2) {
            if (Boolean.TRUE.equals(checksAndPoints[index])) {
                result += (Integer) checksAndPoints[index + 1];
            }
        }
        return result;
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
