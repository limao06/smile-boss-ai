package com.smileboss.sqlai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smileboss.common.BizException;
import com.smileboss.persistence.GeneratedKeyExtractor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Text-to-SQL 持久化边界。应用服务通过有业务含义的方法操作状态，
 * 不直接感知表字段拼装和 JSON 序列化细节。
 */
@Repository
public class TextToSqlStore {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public TextToSqlStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public long createQueryRun(String traceId, String sourceCode, String semanticModelCode,
                               String question, String normalizedQuestion, boolean executeRequested,
                               int maxRows, long userId) {
        return insertAndReturnId("""
                INSERT INTO ai_sql_query_run(
                    tenant_id, trace_id, source_code, semantic_model_code, question,
                    normalized_question, status, execute_requested, max_rows, created_by)
                VALUES(1,?,?,?,?,?,?,?,?,?)
                """, traceId, sourceCode, semanticModelCode, question, normalizedQuestion,
                SqlQueryStatus.GENERATING.name(), executeRequested ? 1 : 0, maxRows, userId);
    }

    public void saveCandidate(long queryRunId, TextToSqlGenerator.SqlDraft draft) {
        jdbcTemplate.update("""
                INSERT INTO ai_sql_candidate(
                    query_run_id, candidate_no, generator, sql_text, rationale, selected_flag)
                VALUES(?,1,?,?,?,1)
                """, queryRunId, draft.generator(), draft.sql(), draft.rationale());
    }

    public void saveGeneratedSql(long queryRunId, TextToSqlGenerator.SqlDraft draft,
                                 SqlSafetyGuard.ValidationResult validation) {
        String status = validation.safe() ? SqlQueryStatus.VALIDATING.name() : SqlQueryStatus.REJECTED.name();
        jdbcTemplate.update("""
                UPDATE ai_sql_query_run
                SET intent_json=?, generated_sql=?, final_sql=?, risk_level=?, status=?
                WHERE id=?
                """, toJson(draft.intent()), draft.sql(), validation.normalizedSql(),
                validation.riskLevel(), status, queryRunId);
    }

    public void saveValidation(long queryRunId, String validatorCode, boolean passed,
                               String riskLevel, Object details) {
        jdbcTemplate.update("""
                INSERT INTO ai_sql_validation(query_run_id, validator_code, passed, risk_level, details_json)
                VALUES(?,?,?,?,?)
                """, queryRunId, validatorCode, passed ? 1 : 0, riskLevel, toJson(details));
    }

    public List<Map<String, Object>> listRuns() {
        return jdbcTemplate.queryForList("""
                SELECT id, trace_id, question, risk_level, status, execute_requested, max_rows, approval_id,
                       error_code, error_message, created_by, created_at, completed_at
                FROM ai_sql_query_run
                ORDER BY id DESC
                """);
    }

    public Map<String, Object> findRun(long queryRunId) {
        Map<String, Object> result = new LinkedHashMap<>(findRunRow(queryRunId));
        decodeJsonColumn(result, "intent_json");
        result.put("candidates", jdbcTemplate.queryForList(
                "SELECT * FROM ai_sql_candidate WHERE query_run_id=? ORDER BY candidate_no", queryRunId));

        List<Map<String, Object>> validations = jdbcTemplate.queryForList(
                "SELECT * FROM ai_sql_validation WHERE query_run_id=? ORDER BY id", queryRunId);
        validations.forEach(row -> decodeJsonColumn(row, "details_json"));
        result.put("validations", validations);

        List<Map<String, Object>> executions = jdbcTemplate.queryForList(
                "SELECT * FROM ai_sql_execution WHERE query_run_id=? ORDER BY id", queryRunId);
        executions.forEach(row -> {
            decodeJsonColumn(row, "columns_json");
            decodeJsonColumn(row, "result_json");
        });
        result.put("executions", executions);
        appendApproval(result);
        return result;
    }

    public Map<String, Object> findRunRow(long queryRunId) {
        return queryOne("SELECT * FROM ai_sql_query_run WHERE id=?", "智能问数运行不存在", queryRunId);
    }

    public void markUnsafe(long queryRunId, List<String> errors) {
        jdbcTemplate.update("""
                UPDATE ai_sql_query_run
                SET error_code='UNSAFE_SQL', error_message=?, completed_at=CURRENT_TIMESTAMP
                WHERE id=?
                """, String.join("; ", errors), queryRunId);
    }

    public void markCompileFailed(long queryRunId, String errorMessage) {
        jdbcTemplate.update("""
                UPDATE ai_sql_query_run
                SET status=?, error_code='SQL_COMPILE_FAILED', error_message=?, completed_at=CURRENT_TIMESTAMP
                WHERE id=?
                """, SqlQueryStatus.REJECTED.name(), errorMessage, queryRunId);
    }

    public void markValidated(long queryRunId) {
        updateStatusAndComplete(queryRunId, SqlQueryStatus.VALIDATED);
    }

    public void markWaitingHuman(long queryRunId, long approvalId) {
        jdbcTemplate.update("UPDATE ai_sql_query_run SET status=?, approval_id=? WHERE id=?",
                SqlQueryStatus.WAITING_HUMAN.name(), approvalId, queryRunId);
    }

    public void markResuming(long queryRunId) {
        jdbcTemplate.update("UPDATE ai_sql_query_run SET status=? WHERE id=?",
                SqlQueryStatus.VALIDATING.name(), queryRunId);
    }

    public void markCompleted(long queryRunId) {
        updateStatusAndComplete(queryRunId, SqlQueryStatus.COMPLETED);
    }

    public void markFailed(long queryRunId, String errorCode, String errorMessage) {
        jdbcTemplate.update("""
                UPDATE ai_sql_query_run
                SET status=?, error_code=?, error_message=?, completed_at=CURRENT_TIMESTAMP
                WHERE id=?
                """, SqlQueryStatus.FAILED.name(), errorCode, errorMessage, queryRunId);
    }

    public void markRejectedByHuman(long queryRunId, String comment) {
        jdbcTemplate.update("""
                UPDATE ai_sql_query_run
                SET status=?, error_code='HUMAN_REJECTED', error_message=?, completed_at=CURRENT_TIMESTAMP
                WHERE id=?
                """, SqlQueryStatus.REJECTED.name(), normalizeText(comment), queryRunId);
    }

    public void saveExecutionSuccess(long queryRunId, String sqlHash, SqlQueryExecutor.QueryResult queryResult,
                                     long durationMillis, long userId) {
        jdbcTemplate.update("""
                INSERT INTO ai_sql_execution(
                    query_run_id, sql_hash, row_count, columns_json, result_json, duration_ms,
                    truncated_flag, status, executed_by)
                VALUES(?,?,?,?,?,?,?,'SUCCEEDED',?)
                """, queryRunId, sqlHash, queryResult.rows().size(), toJson(queryResult.columns()),
                toJson(queryResult.rows()), durationMillis, queryResult.truncated() ? 1 : 0, userId);
    }

    public void saveExecutionFailure(long queryRunId, String sqlHash, long durationMillis,
                                     String errorMessage, long userId) {
        jdbcTemplate.update("""
                INSERT INTO ai_sql_execution(
                    query_run_id, sql_hash, duration_ms, status, error_message, executed_by)
                VALUES(?,?,?,'FAILED',?,?)
                """, queryRunId, sqlHash, durationMillis, errorMessage, userId);
    }

    public void saveFeedback(long queryRunId, Integer rating, String correctionSql,
                             String comment, long userId) {
        queryOne("SELECT id FROM ai_sql_query_run WHERE id=?", "智能问数运行不存在", queryRunId);
        jdbcTemplate.update("""
                INSERT INTO ai_sql_feedback(query_run_id, rating, correction_sql, comment, created_by)
                VALUES(?,?,?,?,?)
                """, queryRunId, rating, normalizeText(correctionSql), normalizeText(comment), userId);
    }

    public long createSyntheticWorkflowRun(long queryRunId, String traceId, String stateJson,
                                           long userId, LocalDateTime expiresAt) {
        return insertAndReturnId("""
                INSERT INTO ai_workflow_run(
                    tenant_id, workflow_code, workflow_version, business_type, business_id, status,
                    state_json, state_version, budget_json, used_budget_json, trace_id, created_by, deadline_at)
                VALUES(1,'TEXT_TO_SQL',1,'SQL_QUERY',?,'WAITING_HUMAN',?,0,'{}','{}',?,?,?)
                """, String.valueOf(queryRunId), stateJson, traceId, userId, Timestamp.valueOf(expiresAt));
    }

    public long createReviewTask(long workflowRunId, long queryRunId) {
        return insertAndReturnId("""
                INSERT INTO ai_workflow_task(
                    run_id, node_code, node_type, status, attempt_no, input_artifact_ids,
                    idempotency_key, started_at)
                VALUES(?,'SQL_REVIEW','HUMAN_APPROVAL','WAITING_HUMAN',1,'[]',?,CURRENT_TIMESTAMP)
                """, workflowRunId, "text-to-sql:" + queryRunId + ":review");
    }

    public long createSensitiveApproval(long workflowRunId, long taskId, long queryRunId,
                                        String requestToken, String riskLevel, LocalDateTime expiresAt,
                                        Object requestPayload, String snapshotHash) {
        return insertAndReturnId("""
                INSERT INTO ai_human_approval(
                    run_id, task_id, node_code, approval_type, origin_type, origin_id, request_token,
                    policy_code, policy_version, risk_level, reviewer_group, due_at, timeout_action,
                    title, instruction, request_payload_json, input_snapshot_hash, status, expires_at)
                VALUES(?,?,'SQL_REVIEW','TEXT_TO_SQL','SQL_QUERY_RUN',?,?,'TEXT_TO_SQL_SENSITIVE_REVIEW',
                       1,?,'DATA_REVIEWER',?,'REJECT','审核智能问数敏感查询',
                       '核对问题、SQL、敏感字段和使用目的后决定是否允许执行',?,?,'PENDING',?)
                """, workflowRunId, taskId, queryRunId, requestToken, riskLevel, Timestamp.valueOf(expiresAt),
                toJson(requestPayload), snapshotHash, Timestamp.valueOf(expiresAt));
    }

    public void updateSyntheticTaskDecision(long taskId, boolean approved) {
        jdbcTemplate.update("""
                UPDATE ai_workflow_task SET status=?, finished_at=CURRENT_TIMESTAMP WHERE id=?
                """, approved ? "SUCCEEDED" : "CANCELLED", taskId);
    }

    public void markSyntheticWorkflowRunning(long workflowRunId) {
        jdbcTemplate.update("UPDATE ai_workflow_run SET status='RUNNING' WHERE id=?", workflowRunId);
    }

    public void completeSyntheticWorkflow(long workflowRunId) {
        jdbcTemplate.update("""
                UPDATE ai_workflow_run SET status='COMPLETED', completed_at=CURRENT_TIMESTAMP WHERE id=?
                """, workflowRunId);
    }

    public void cancelSyntheticWorkflow(long workflowRunId) {
        jdbcTemplate.update("""
                UPDATE ai_workflow_run SET status='CANCELLED', completed_at=CURRENT_TIMESTAMP WHERE id=?
                """, workflowRunId);
    }

    public void failSyntheticWorkflow(long workflowRunId) {
        jdbcTemplate.update("""
                UPDATE ai_workflow_run SET status='FAILED', completed_at=CURRENT_TIMESTAMP WHERE id=?
                """, workflowRunId);
    }

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new BizException("JSON 序列化失败：" + exception.getOriginalMessage());
        }
    }

    private void updateStatusAndComplete(long queryRunId, SqlQueryStatus status) {
        jdbcTemplate.update("""
                UPDATE ai_sql_query_run SET status=?, completed_at=CURRENT_TIMESTAMP WHERE id=?
                """, status.name(), queryRunId);
    }

    private void appendApproval(Map<String, Object> queryRun) {
        Object approvalId = queryRun.get("approval_id");
        if (approvalId == null) {
            return;
        }
        Map<String, Object> approval = new LinkedHashMap<>(queryOne(
                "SELECT * FROM ai_human_approval WHERE id=?", "人工审核任务不存在", approvalId));
        decodeJsonColumn(approval, "request_payload_json");
        decodeJsonColumn(approval, "decision_payload_json");
        queryRun.put("approval", approval);
    }

    private Map<String, Object> queryOne(String sql, String notFoundMessage, Object... parameters) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, parameters);
        if (rows.isEmpty()) {
            throw new BizException(notFoundMessage);
        }
        return rows.get(0);
    }

    private long insertAndReturnId(String sql, Object... parameters) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            return statement;
        }, keyHolder);
        return GeneratedKeyExtractor.extractId(keyHolder);
    }

    private void decodeJsonColumn(Map<String, Object> row, String columnName) {
        Object value = row.get(columnName);
        if (value == null || String.valueOf(value).isBlank()) {
            return;
        }
        try {
            row.put(columnName, objectMapper.readValue(String.valueOf(value), Object.class));
        } catch (JsonProcessingException exception) {
            throw new BizException("数据库 JSON 字段格式错误：" + columnName);
        }
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }
}
