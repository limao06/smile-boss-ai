package com.smileboss.sqlai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Text-to-SQL 可调安全参数。生产环境可以通过 application.yml 或环境变量覆盖，
 * 无需修改业务代码。
 */
@Component
@ConfigurationProperties(prefix = "smile.text-to-sql")
public class TextToSqlProperties {
    private String sourceCode = "RECRUITMENT_MAIN";
    private String semanticModelCode = "RECRUITMENT_ANALYTICS";
    private int defaultMaxRows = 200;
    private int maximumRows = 1000;
    private int queryTimeoutSeconds = 15;
    private int approvalSlaMinutes = 240;
    private Set<String> allowedTables = new LinkedHashSet<>(Set.of(
            "recruit_job", "talent_candidate", "talent_resume", "recruit_recommendation",
            "mock_interview_session", "ai_interview_invitation", "ai_interview_session",
            "candidate_activity_event"));

    public int normalizeMaxRows(Integer requestedRows) {
        int rows = requestedRows == null ? defaultMaxRows : requestedRows;
        return Math.max(1, Math.min(rows, maximumRows));
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public String getSemanticModelCode() {
        return semanticModelCode;
    }

    public void setSemanticModelCode(String semanticModelCode) {
        this.semanticModelCode = semanticModelCode;
    }

    public int getDefaultMaxRows() {
        return defaultMaxRows;
    }

    public void setDefaultMaxRows(int defaultMaxRows) {
        this.defaultMaxRows = defaultMaxRows;
    }

    public int getMaximumRows() {
        return maximumRows;
    }

    public void setMaximumRows(int maximumRows) {
        this.maximumRows = maximumRows;
    }

    public int getQueryTimeoutSeconds() {
        return queryTimeoutSeconds;
    }

    public void setQueryTimeoutSeconds(int queryTimeoutSeconds) {
        this.queryTimeoutSeconds = queryTimeoutSeconds;
    }

    public int getApprovalSlaMinutes() {
        return approvalSlaMinutes;
    }

    public void setApprovalSlaMinutes(int approvalSlaMinutes) {
        this.approvalSlaMinutes = approvalSlaMinutes;
    }

    public Set<String> getAllowedTables() {
        return Set.copyOf(allowedTables);
    }

    public void setAllowedTables(Set<String> allowedTables) {
        this.allowedTables = new LinkedHashSet<>(allowedTables);
    }
}
