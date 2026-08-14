package com.smileboss.dingtalk;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** 生成不含简历正文、联系方式和 SQL 原文的审核通知。 */
@Component
public class DingTalkApprovalMessageFactory {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final DingTalkProperties properties;

    public DingTalkApprovalMessageFactory(DingTalkProperties properties) {
        this.properties = properties;
    }

    public Message create(Map<String, Object> approval) {
        long approvalId = longValue(approval.get("id"));
        String title = safeText(approval.get("title"), "待人工审核任务");
        String riskLevel = safeText(approval.get("risk_level"), "L1");
        String approvalType = safeText(approval.get("approval_type"), "WORKFLOW");
        String reviewerGroup = safeText(approval.get("reviewer_group"), "HR_ADMIN");
        String dueAt = formatDate(approval.get("due_at"));
        String approvalUrl = properties.approvalUrl(approvalId);

        String markdown = "### [" + riskLevel + "] " + escapeMarkdown(title) + "\n\n"
                + "> 审核编号：`" + approvalId + "`\n\n"
                + "- 审核类型：`" + escapeMarkdown(approvalType) + "`\n"
                + "- 审核组：`" + escapeMarkdown(reviewerGroup) + "`\n"
                + "- 到期时间：`" + dueAt + "`\n\n"
                + "[打开 SmileBoss 审核工作台](" + approvalUrl + ")\n\n"
                + "> 为保护候选人隐私，钉钉消息不展示简历正文、联系方式、模型上下文或 SQL 原文。";
        return new Message("SmileBoss 人工审核 #" + approvalId, markdown);
    }

    private static String formatDate(Object value) {
        if (value == null) {
            return "未设置";
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime().format(DATE_TIME);
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.format(DATE_TIME);
        }
        return escapeMarkdown(String.valueOf(value));
    }

    private static long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }

    private static String safeText(Object value, String fallback) {
        String text = value == null ? "" : String.valueOf(value).trim();
        return text.isBlank() ? fallback : text;
    }

    private static String escapeMarkdown(String value) {
        return value.replace("[", "［").replace("]", "］")
                .replace("(", "（").replace(")", "）")
                .replace("`", "'").replace("\r", " ").replace("\n", " ");
    }

    public record Message(String title, String markdown) {}
}
