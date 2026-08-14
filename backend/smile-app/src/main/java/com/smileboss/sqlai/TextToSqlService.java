package com.smileboss.sqlai;

import com.smileboss.approval.HumanApprovalService;
import com.smileboss.common.BizException;
import com.smileboss.security.HashUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Text-to-SQL 应用服务，只负责编排执行阶段。
 *
 * <p>运行顺序：创建运行 → 生成候选 → 静态校验 → EXPLAIN → 风险路由 →
 * 自动执行或等待人工审核。模型、SQL 解析、JDBC 执行和持久化分别由独立组件负责。</p>
 */
@Service
public class TextToSqlService {
    private static final String APPROVE = "APPROVE";
    private static final String STATIC_SQL_GUARD = "STATIC_SQL_GUARD";
    private static final String DATABASE_EXPLAIN = "DATABASE_EXPLAIN";

    private final TextToSqlStore store;
    private final SqlSafetyGuard sqlSafetyGuard;
    private final TextToSqlGenerator sqlGenerator;
    private final SqlQueryExecutor sqlQueryExecutor;
    private final TextToSqlProperties properties;
    private final HumanApprovalService approvalService;

    public TextToSqlService(TextToSqlStore store,
                            SqlSafetyGuard sqlSafetyGuard,
                            TextToSqlGenerator sqlGenerator,
                            SqlQueryExecutor sqlQueryExecutor,
                            TextToSqlProperties properties,
                            HumanApprovalService approvalService) {
        this.store = store;
        this.sqlSafetyGuard = sqlSafetyGuard;
        this.sqlGenerator = sqlGenerator;
        this.sqlQueryExecutor = sqlQueryExecutor;
        this.properties = properties;
        this.approvalService = approvalService;
    }

    /**
     * 提交自然语言问题。这里故意不使用长事务，避免模型调用和分析 SQL 持有数据库事务。
     */
    public Map<String, Object> ask(QueryRequest request, long userId) {
        String question = requireQuestion(request.question());
        int maxRows = properties.normalizeMaxRows(request.maxRows());
        boolean executeRequested = Boolean.TRUE.equals(request.execute());
        String traceId = newTraceId();
        long queryRunId = store.createQueryRun(
                traceId,
                properties.getSourceCode(),
                properties.getSemanticModelCode(),
                question,
                normalizeQuestion(question),
                executeRequested,
                maxRows,
                userId);

        try {
            generateValidateAndRoute(queryRunId, question, traceId, executeRequested, maxRows, userId);
        } catch (RuntimeException exception) {
            store.markFailed(queryRunId, "TEXT_TO_SQL_FAILED", safeMessage(exception));
        }
        return store.findRun(queryRunId);
    }

    private void generateValidateAndRoute(long queryRunId, String question, String traceId,
                                          boolean executeRequested, int maxRows, long userId) {
        TextToSqlGenerator.SqlDraft draft = sqlGenerator.generate(question);
        store.saveCandidate(queryRunId, draft);

        SqlSafetyGuard.ValidationResult validation = sqlSafetyGuard.validate(
                draft.sql(), properties.getAllowedTables(), maxRows);
        store.saveGeneratedSql(queryRunId, draft, validation);
        store.saveValidation(queryRunId, STATIC_SQL_GUARD, validation.safe(), validation.riskLevel(), Map.of(
                "errors", validation.errors(),
                "warnings", validation.warnings(),
                "tables", validation.tables(),
                "sensitiveColumns", validation.sensitiveColumns(),
                "appliedLimit", validation.appliedLimit()));
        if (!validation.safe()) {
            store.markUnsafe(queryRunId, validation.errors());
            return;
        }

        SqlQueryExecutor.ExplainResult explainResult = sqlQueryExecutor.explain(validation.normalizedSql());
        store.saveValidation(queryRunId, DATABASE_EXPLAIN, explainResult.passed(), validation.riskLevel(), Map.of(
                "message", explainResult.message(),
                "plan", explainResult.plan()));
        if (!explainResult.passed()) {
            store.markCompileFailed(queryRunId, explainResult.message());
            return;
        }

        routeValidatedQuery(queryRunId, traceId, question, validation, executeRequested, maxRows, userId);
    }

    private void routeValidatedQuery(long queryRunId, String traceId, String question,
                                     SqlSafetyGuard.ValidationResult validation,
                                     boolean executeRequested, int maxRows, long userId) {
        if (!executeRequested) {
            store.markValidated(queryRunId);
            return;
        }

        SqlRiskLevel riskLevel = SqlRiskLevel.valueOf(validation.riskLevel());
        if (riskLevel.requiresHumanReview()) {
            createSensitiveQueryApproval(queryRunId, traceId, question, validation, userId);
            return;
        }
        executeValidatedQuery(queryRunId, validation.normalizedSql(), maxRows, userId);
    }

    public List<Map<String, Object>> listRuns() {
        return store.listRuns();
    }

    public Map<String, Object> run(long queryRunId) {
        return store.findRun(queryRunId);
    }

    /**
     * 审核 CAS 更新使用短事务；实际查询在事务外执行，避免长查询长期占用事务资源。
     */
    public Map<String, Object> decide(long queryRunId, DecisionRequest request, long userId) {
        String decision = approvalService.normalizeDecision(request.decision());
        Map<String, Object> queryRun = requireWaitingHumanRun(queryRunId);
        long approvalId = longValue(queryRun.get("approval_id"));
        Map<String, Object> approval = approvalService.findById(approvalId);
        approvalService.requirePending(approval);

        String finalSql = stringValue(queryRun.get("final_sql"));
        verifyApprovalSnapshot(approval, finalSql);
        approvalService.decide(
                approvalId,
                intValue(approval.get("decision_version")),
                decision,
                request.comment(),
                userId);

        boolean approved = APPROVE.equals(decision);
        updateSyntheticWorkflowDecision(approval, approved);
        if (!approved) {
            store.markRejectedByHuman(queryRunId, request.comment());
            store.cancelSyntheticWorkflow(longValue(approval.get("run_id")));
            return store.findRun(queryRunId);
        }

        store.markResuming(queryRunId);
        boolean executionSucceeded = executeValidatedQuery(
                queryRunId, finalSql, intValue(queryRun.get("max_rows")), userId);
        long workflowRunId = longValue(approval.get("run_id"));
        if (executionSucceeded) {
            store.completeSyntheticWorkflow(workflowRunId);
        } else {
            store.failSyntheticWorkflow(workflowRunId);
        }
        return store.findRun(queryRunId);
    }

    @Transactional
    public Map<String, Object> feedback(long queryRunId, FeedbackRequest request, long userId) {
        Integer rating = request.rating() == null ? null : Math.max(1, Math.min(5, request.rating()));
        store.saveFeedback(queryRunId, rating, request.correctionSql(), request.comment(), userId);
        return Map.of("queryRunId", queryRunId, "saved", true);
    }

    private boolean executeValidatedQuery(long queryRunId, String finalSql, int maxRows, long userId) {
        long startedAt = System.currentTimeMillis();
        String sqlHash = HashUtils.sha256(finalSql);
        try {
            SqlQueryExecutor.QueryResult queryResult = sqlQueryExecutor.execute(finalSql, maxRows);
            store.saveExecutionSuccess(queryRunId, sqlHash, queryResult, elapsedMillis(startedAt), userId);
            store.markCompleted(queryRunId);
            return true;
        } catch (RuntimeException exception) {
            String errorMessage = safeMessage(exception);
            store.saveExecutionFailure(queryRunId, sqlHash, elapsedMillis(startedAt), errorMessage, userId);
            store.markFailed(queryRunId, "SQL_EXECUTION_FAILED", errorMessage);
            return false;
        }
    }

    private void createSensitiveQueryApproval(long queryRunId, String traceId, String question,
                                              SqlSafetyGuard.ValidationResult validation, long userId) {
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(properties.getApprovalSlaMinutes());
        String sqlHash = HashUtils.sha256(validation.normalizedSql());
        long workflowRunId = store.createSyntheticWorkflowRun(
                queryRunId,
                traceId,
                store.toJson(Map.of("queryRunId", queryRunId, "sqlHash", sqlHash)),
                userId,
                expiresAt);
        long taskId = store.createReviewTask(workflowRunId, queryRunId);
        long approvalId = store.createSensitiveApproval(
                workflowRunId,
                taskId,
                queryRunId,
                newTraceId(),
                validation.riskLevel(),
                expiresAt,
                Map.of(
                        "question", question,
                        "sql", validation.normalizedSql(),
                        "tables", validation.tables(),
                        "sensitiveColumns", validation.sensitiveColumns(),
                        "requestedBy", userId),
                sqlHash);
        store.markWaitingHuman(queryRunId, approvalId);
        approvalService.recordRequest(approvalId, userId,
                Map.of("riskLevel", validation.riskLevel()));
    }

    private Map<String, Object> requireWaitingHumanRun(long queryRunId) {
        Map<String, Object> queryRun = store.findRunRow(queryRunId);
        if (!SqlQueryStatus.WAITING_HUMAN.name().equals(stringValue(queryRun.get("status")))) {
            throw new BizException("该查询当前不等待人工审核");
        }
        return queryRun;
    }

    private void verifyApprovalSnapshot(Map<String, Object> approval, String finalSql) {
        String expectedHash = stringValue(approval.get("input_snapshot_hash"));
        if (!HashUtils.sha256(finalSql).equals(expectedHash)) {
            throw new BizException("查询快照已变化，请重新发起审核");
        }
    }

    private void updateSyntheticWorkflowDecision(Map<String, Object> approval, boolean approved) {
        store.updateSyntheticTaskDecision(longValue(approval.get("task_id")), approved);
        if (approved) {
            store.markSyntheticWorkflowRunning(longValue(approval.get("run_id")));
        }
    }

    private static String requireQuestion(String value) {
        String question = value == null ? "" : value.trim();
        if (question.isBlank()) {
            throw new BizException("问题不能为空");
        }
        return question;
    }

    private static String normalizeQuestion(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }

    private static long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }

    private static long elapsedMillis(long startedAt) {
        return System.currentTimeMillis() - startedAt;
    }

    private static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    public record QueryRequest(String question, Boolean execute, Integer maxRows) {}
    public record DecisionRequest(String decision, String comment) {}
    public record FeedbackRequest(Integer rating, String correctionSql, String comment) {}
}
