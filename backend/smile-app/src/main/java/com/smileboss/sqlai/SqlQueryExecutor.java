package com.smileboss.sqlai;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 只负责数据库的预检和受限查询，不负责 SQL 生成、风险判断或状态持久化。
 */
@Component
public class SqlQueryExecutor {
    private final JdbcTemplate jdbcTemplate;
    private final TextToSqlProperties properties;

    public SqlQueryExecutor(JdbcTemplate jdbcTemplate, TextToSqlProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    public ExplainResult explain(String validatedSql) {
        try {
            // validatedSql 已经过 SqlSafetyGuard；这里拼接 EXPLAIN 是数据库预检，不接受原始用户输入。
            List<Map<String, Object>> plan = jdbcTemplate.queryForList("EXPLAIN " + validatedSql);
            return ExplainResult.passed(plan);
        } catch (RuntimeException exception) {
            return ExplainResult.failed(safeMessage(exception));
        }
    }

    public QueryResult execute(String validatedSql, int maxRows) {
        return jdbcTemplate.query(connection -> {
            PreparedStatement statement = connection.prepareStatement(validatedSql);
            statement.setMaxRows(maxRows + 1);
            statement.setQueryTimeout(properties.getQueryTimeoutSeconds());
            return statement;
        }, resultSet -> {
            ResultSetMetaData metadata = resultSet.getMetaData();
            List<String> columns = readColumnLabels(metadata);
            List<Map<String, Object>> rows = new ArrayList<>();
            boolean truncated = false;
            while (resultSet.next()) {
                if (rows.size() >= maxRows) {
                    truncated = true;
                    break;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                for (int columnIndex = 1; columnIndex <= metadata.getColumnCount(); columnIndex++) {
                    row.put(columns.get(columnIndex - 1), resultSet.getObject(columnIndex));
                }
                rows.add(row);
            }
            return new QueryResult(List.copyOf(columns), List.copyOf(rows), truncated);
        });
    }

    private static List<String> readColumnLabels(ResultSetMetaData metadata) throws java.sql.SQLException {
        List<String> columns = new ArrayList<>();
        for (int columnIndex = 1; columnIndex <= metadata.getColumnCount(); columnIndex++) {
            columns.add(metadata.getColumnLabel(columnIndex));
        }
        return columns;
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    public record ExplainResult(boolean passed, String message, List<Map<String, Object>> plan) {
        static ExplainResult passed(List<Map<String, Object>> plan) {
            return new ExplainResult(true, "SQL编译和执行计划检查通过", List.copyOf(plan));
        }

        static ExplainResult failed(String message) {
            return new ExplainResult(false, message, List.of());
        }
    }

    public record QueryResult(List<String> columns, List<Map<String, Object>> rows, boolean truncated) {}
}
