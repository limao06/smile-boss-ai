package com.smileboss.sqlai;

/** Text-to-SQL 查询运行状态。状态值与 ai_sql_query_run.status 保持一致。 */
public enum SqlQueryStatus {
    GENERATING,
    VALIDATING,
    VALIDATED,
    WAITING_HUMAN,
    COMPLETED,
    REJECTED,
    FAILED
}
