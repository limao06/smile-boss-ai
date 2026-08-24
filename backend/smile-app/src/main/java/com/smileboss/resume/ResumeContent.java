package com.smileboss.resume;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 在线简历的稳定领域模型。原始导入文本保留为事实证据，展示和导出只使用结构化字段。
 */
public record ResumeContent(
        String name,
        String phone,
        String email,
        String city,
        String desiredPosition,
        int yearsOfExperience,
        String summary,
        List<String> skills,
        String workExperience,
        String projectExperience,
        String education,
        String certificates,
        String sourceText
) {
    public ResumeContent {
        name = clean(name);
        phone = clean(phone);
        email = clean(email);
        city = clean(city);
        desiredPosition = clean(desiredPosition);
        yearsOfExperience = Math.max(0, yearsOfExperience);
        summary = clean(summary);
        skills = skills == null ? List.of() : List.copyOf(new LinkedHashSet<>(skills.stream()
                .map(ResumeContent::clean).filter(value -> !value.isBlank()).toList()));
        workExperience = clean(workExperience);
        projectExperience = clean(projectExperience);
        education = clean(education);
        certificates = clean(certificates);
        sourceText = clean(sourceText);
    }

    public static ResumeContent fromImport(Map<String, Object> candidate,
                                           Map<String, Object> structured,
                                           String sourceText) {
        return new ResumeContent(
                first(structured.get("name"), candidate.get("name")),
                string(structured.get("phone")),
                string(structured.get("email")),
                first(structured.get("city"), candidate.get("city")),
                string(candidate.get("desired_position")),
                integer(firstObject(structured.get("yearsOfExperience"), candidate.get("years_of_experience"))),
                first(structured.get("summary"), candidate.get("profile_summary")),
                stringList(structured.get("skills")),
                section(sourceText, "工作经历", "工作经验"),
                section(sourceText, "项目经历", "项目经验"),
                section(sourceText, "教育经历", "教育背景"),
                section(sourceText, "证书", "资格认证"),
                sourceText
        );
    }

    public ResumeContent withSummary(String value) {
        return new ResumeContent(name, phone, email, city, desiredPosition, yearsOfExperience,
                value, skills, workExperience, projectExperience, education, certificates, sourceText);
    }

    public ResumeContent withDesiredPosition(String value) {
        return new ResumeContent(name, phone, email, city, value, yearsOfExperience,
                summary, skills, workExperience, projectExperience, education, certificates, sourceText);
    }

    /**
     * 导入文件只覆盖其中明确存在的字段；缺失字段保留当前在线简历，技能做稳定去重合并。
     * 冲突值仍保存在待确认版本中，由候选人预览后决定是否发布。
     */
    public ResumeContent mergeImport(ResumeContent imported) {
        LinkedHashSet<String> mergedSkills = new LinkedHashSet<>(skills);
        mergedSkills.addAll(imported.skills);
        return new ResumeContent(
                preferred(imported.name, name),
                preferred(imported.phone, phone),
                preferred(imported.email, email),
                preferred(imported.city, city),
                preferred(imported.desiredPosition, desiredPosition),
                imported.yearsOfExperience > 0 ? imported.yearsOfExperience : yearsOfExperience,
                preferred(imported.summary, summary),
                List.copyOf(mergedSkills),
                preferred(imported.workExperience, workExperience),
                preferred(imported.projectExperience, projectExperience),
                preferred(imported.education, education),
                preferred(imported.certificates, certificates),
                preferred(imported.sourceText, sourceText)
        );
    }

    private static String section(String text, String... headings) {
        String cleanText = clean(text);
        for (String heading : headings) {
            int start = cleanText.indexOf(heading);
            if (start >= 0) {
                int end = Math.min(cleanText.length(), start + 1200);
                return cleanText.substring(start, end);
            }
        }
        return "";
    }

    private static Object firstObject(Object preferred, Object fallback) {
        return preferred == null || String.valueOf(preferred).isBlank() ? fallback : preferred;
    }

    private static String first(Object preferred, Object fallback) {
        return string(firstObject(preferred, fallback));
    }

    private static String preferred(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    private static List<String> stringList(Object value) {
        return value instanceof List<?> values
                ? values.stream().map(String::valueOf).toList()
                : List.of();
    }

    private static int integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(string(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace("\u0000", "").trim();
    }
}
