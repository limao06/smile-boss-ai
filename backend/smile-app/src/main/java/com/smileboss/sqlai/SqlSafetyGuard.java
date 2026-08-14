package com.smileboss.sqlai;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 生成式 SQL 的确定性安全边界。
 *
 * <p>LLM 输出是不可信输入。只有通过单语句、只读关键字、表白名单、敏感字段和行数检查的
 * SQL 才能进入数据库 EXPLAIN。生产数据库仍应使用仅有 SELECT 权限的独立账号。</p>
 */
@Component
public class SqlSafetyGuard {
    private static final int ABSOLUTE_MAX_ROWS = 1000;
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*|[(),.*;]");
    private static final Pattern WORD = Pattern.compile("[A-Z_][A-Z0-9_$]*");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern SERVER_FILE_EXPORT =
            Pattern.compile("(?s).*\\bINTO\\s+(OUTFILE|DUMPFILE)\\b.*");
    private static final Pattern ROW_LOCK = Pattern.compile("(?s).*\\bFOR\\s+UPDATE\\b.*");
    private static final Pattern SELECT_ALL = Pattern.compile("(?s).*SELECT\\s+(DISTINCT\\s+)?\\*.*");

    private static final Set<String> QUERY_START_KEYWORDS = Set.of("SELECT", "WITH");
    private static final Set<String> SOURCE_KEYWORDS = Set.of("FROM", "JOIN");
    private static final Set<String> CLAUSE_TERMINATORS =
            Set.of("WHERE", "GROUP", "HAVING", "ORDER", "LIMIT", "UNION", "EXCEPT", "INTERSECT");
    private static final Set<String> AGGREGATE_FUNCTIONS = Set.of("COUNT", "SUM", "AVG", "MIN", "MAX");
    private static final Set<String> DENIED_KEYWORDS = Set.of(
            "INSERT", "UPDATE", "DELETE", "MERGE", "REPLACE", "UPSERT",
            "CREATE", "ALTER", "DROP", "TRUNCATE", "RENAME", "GRANT", "REVOKE",
            "CALL", "EXEC", "EXECUTE", "LOAD", "COPY", "LOCK", "UNLOCK",
            "HANDLER", "DO", "SET", "USE", "ATTACH", "DETACH", "INTO", "PROCEDURE");
    private static final Set<String> DANGEROUS_FUNCTIONS = Set.of(
            "SLEEP", "BENCHMARK", "LOAD_FILE", "PG_SLEEP", "DBMS_LOCK");
    private static final Set<String> SENSITIVE_COLUMNS = Set.of(
            "PHONE", "EMAIL", "NAME", "RAW_TEXT", "STRUCTURED_JSON", "PROFILE_SUMMARY",
            "ANSWER", "EVIDENCE", "PASSWORD", "TOKEN", "METADATA_JSON");

    public ValidationResult validate(String candidateSql, Set<String> allowedTables, int requestedMaxRows) {
        int maxRows = Math.max(1, Math.min(requestedMaxRows, ABSOLUTE_MAX_ROWS));
        if (candidateSql == null || candidateSql.isBlank()) {
            return rejectedEmptySql(maxRows);
        }

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        String sql = stripTrailingSemicolon(candidateSql.trim());
        String visibleSql = removeCommentsAndMaskStrings(candidateSql.trim());
        if (containsInnerSemicolon(visibleSql)) {
            errors.add("只允许执行一条SQL语句");
        }

        visibleSql = removeCommentsAndMaskStrings(sql);
        List<String> tokens = tokenize(visibleSql);
        validateKeywords(tokens, visibleSql, errors);

        Set<String> commonTableExpressions = findCommonTableExpressions(tokens);
        Set<String> referencedTables = findReferencedTables(tokens, commonTableExpressions);
        validateTableWhitelist(referencedTables, allowedTables, errors);

        Set<String> sensitiveColumns = findSensitiveColumns(tokens);
        if (SELECT_ALL.matcher(visibleSql.toUpperCase(Locale.ROOT)).matches()) {
            sensitiveColumns.add("*");
            warnings.add("SELECT * 会扩大数据暴露面，已提升到人工审核级别");
        }

        String normalizedSql = WHITESPACE.matcher(sql).replaceAll(" ").trim();
        if (!tokens.contains("LIMIT") && errors.isEmpty()) {
            normalizedSql = normalizedSql + " LIMIT " + maxRows;
            warnings.add("已自动添加行数上限 LIMIT " + maxRows);
        }
        String riskLevel = determineRiskLevel(tokens, sensitiveColumns, errors);
        return new ValidationResult(
                errors.isEmpty(),
                normalizedSql,
                riskLevel,
                List.copyOf(errors),
                List.copyOf(warnings),
                Set.copyOf(referencedTables),
                Set.copyOf(sensitiveColumns),
                maxRows);
    }

    private void validateKeywords(List<String> tokens, String visibleSql, List<String> errors) {
        String firstKeyword = tokens.stream().filter(this::isWord).findFirst().orElse("");
        if (!QUERY_START_KEYWORDS.contains(firstKeyword)) {
            errors.add("只允许SELECT或WITH查询");
        }
        for (String token : tokens) {
            if (DENIED_KEYWORDS.contains(token)) {
                errors.add("检测到禁止关键字: " + token);
            }
            if (DANGEROUS_FUNCTIONS.contains(token)) {
                errors.add("检测到危险函数: " + token);
            }
        }

        String upperSql = visibleSql.toUpperCase(Locale.ROOT);
        if (SERVER_FILE_EXPORT.matcher(upperSql).matches()) {
            errors.add("禁止导出服务器文件");
        }
        if (ROW_LOCK.matcher(upperSql).matches()) {
            errors.add("禁止行锁查询");
        }
    }

    private void validateTableWhitelist(Set<String> referencedTables, Set<String> allowedTables,
                                        List<String> errors) {
        if (referencedTables.isEmpty()) {
            errors.add("查询必须引用语义模型授权的数据表");
            return;
        }
        Set<String> normalizedAllowedTables = new LinkedHashSet<>();
        for (String allowedTable : allowedTables) {
            normalizedAllowedTables.add(allowedTable.toUpperCase(Locale.ROOT));
        }
        for (String referencedTable : referencedTables) {
            String simpleTableName = simpleTableName(referencedTable);
            if (!normalizedAllowedTables.contains(simpleTableName)) {
                errors.add("未授权访问数据表: " + referencedTable);
            }
        }
    }

    private String determineRiskLevel(List<String> tokens, Set<String> sensitiveColumns, List<String> errors) {
        if (!errors.isEmpty()) {
            return SqlRiskLevel.L3.name();
        }
        if (!sensitiveColumns.isEmpty()) {
            return SqlRiskLevel.L2.name();
        }
        long joinCount = tokens.stream().filter("JOIN"::equals).count();
        boolean containsAggregate = tokens.stream().anyMatch(AGGREGATE_FUNCTIONS::contains);
        return containsAggregate && joinCount <= 2 ? SqlRiskLevel.L0.name() : SqlRiskLevel.L1.name();
    }

    private Set<String> findCommonTableExpressions(List<String> tokens) {
        Set<String> expressions = new LinkedHashSet<>();
        if (tokens.isEmpty() || !"WITH".equals(tokens.get(0))) {
            return expressions;
        }
        for (int index = 1; index + 1 < tokens.size(); index++) {
            if (isWord(tokens.get(index)) && "AS".equals(tokens.get(index + 1))) {
                expressions.add(tokens.get(index));
            }
            if ("SELECT".equals(tokens.get(index))) {
                break;
            }
        }
        return expressions;
    }

    /**
     * 按括号深度跟踪 FROM 子句，可同时识别 JOIN、子查询和逗号连接，
     * 避免通过旧式逗号连接绕过表白名单。
     */
    private Set<String> findReferencedTables(List<String> tokens, Set<String> commonTableExpressions) {
        Set<String> referencedTables = new LinkedHashSet<>();
        Map<Integer, Boolean> insideFromClause = new HashMap<>();
        int parenthesisDepth = 0;
        for (int index = 0; index < tokens.size(); index++) {
            String token = tokens.get(index);
            if ("(".equals(token)) {
                parenthesisDepth++;
                continue;
            }
            if (")".equals(token)) {
                insideFromClause.remove(parenthesisDepth);
                parenthesisDepth = Math.max(0, parenthesisDepth - 1);
                continue;
            }
            if (CLAUSE_TERMINATORS.contains(token)) {
                insideFromClause.put(parenthesisDepth, false);
            }

            boolean sourceExpected = SOURCE_KEYWORDS.contains(token)
                    || (",".equals(token) && Boolean.TRUE.equals(insideFromClause.get(parenthesisDepth)));
            if (!sourceExpected) {
                continue;
            }
            if ("FROM".equals(token)) {
                insideFromClause.put(parenthesisDepth, true);
            }
            addNextTable(tokens, index + 1, commonTableExpressions, referencedTables);
        }
        return referencedTables;
    }

    private void addNextTable(List<String> tokens, int cursor, Set<String> commonTableExpressions,
                              Set<String> referencedTables) {
        if (cursor >= tokens.size() || "(".equals(tokens.get(cursor)) || "SELECT".equals(tokens.get(cursor))) {
            return;
        }
        String table = tokens.get(cursor);
        if (cursor + 2 < tokens.size() && ".".equals(tokens.get(cursor + 1))) {
            table = table + "." + tokens.get(cursor + 2);
        }
        if (isWord(table.replace(".", "")) && !commonTableExpressions.contains(table)) {
            referencedTables.add(table);
        }
    }

    private Set<String> findSensitiveColumns(List<String> tokens) {
        Set<String> sensitiveColumns = new LinkedHashSet<>();
        for (String token : tokens) {
            if (SENSITIVE_COLUMNS.contains(token)) {
                sensitiveColumns.add(token.toLowerCase(Locale.ROOT));
            }
        }
        return sensitiveColumns;
    }

    private List<String> tokenize(String visibleSql) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(visibleSql.toUpperCase(Locale.ROOT));
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    private ValidationResult rejectedEmptySql(int maxRows) {
        return new ValidationResult(
                false, "", SqlRiskLevel.L3.name(), List.of("SQL不能为空"), List.of(), Set.of(), Set.of(), maxRows);
    }

    private boolean containsInnerSemicolon(String visibleSql) {
        String trimmed = visibleSql.trim();
        int firstSemicolon = trimmed.indexOf(';');
        return firstSemicolon >= 0 && firstSemicolon != trimmed.length() - 1;
    }

    private String stripTrailingSemicolon(String sql) {
        String trimmed = sql.trim();
        return trimmed.endsWith(";") ? trimmed.substring(0, trimmed.length() - 1).trim() : trimmed;
    }

    private boolean isWord(String token) {
        return token != null && WORD.matcher(token).matches();
    }

    private static String simpleTableName(String table) {
        return table.contains(".") ? table.substring(table.lastIndexOf('.') + 1) : table;
    }

    /**
     * 删除注释并屏蔽字符串内容，同时保留字符串长度和标识符字符，使后续分词位置稳定。
     */
    static String removeCommentsAndMaskStrings(String sql) {
        StringBuilder visibleSql = new StringBuilder(sql.length());
        ScanState state = ScanState.NORMAL;
        char identifierQuote = '\0';
        for (int index = 0; index < sql.length(); index++) {
            char current = sql.charAt(index);
            char next = index + 1 < sql.length() ? sql.charAt(index + 1) : '\0';
            switch (state) {
                case NORMAL -> {
                    if (current == '-' && next == '-') {
                        state = ScanState.LINE_COMMENT;
                        visibleSql.append("  ");
                        index++;
                    } else if (current == '/' && next == '*') {
                        state = ScanState.BLOCK_COMMENT;
                        visibleSql.append("  ");
                        index++;
                    } else if (current == '\'') {
                        state = ScanState.STRING_LITERAL;
                        visibleSql.append(' ');
                    } else if (current == '`' || current == '"') {
                        state = ScanState.QUOTED_IDENTIFIER;
                        identifierQuote = current;
                        visibleSql.append(' ');
                    } else {
                        visibleSql.append(current);
                    }
                }
                case LINE_COMMENT -> {
                    visibleSql.append(' ');
                    if (current == '\n' || current == '\r') {
                        state = ScanState.NORMAL;
                    }
                }
                case BLOCK_COMMENT -> {
                    visibleSql.append(' ');
                    if (current == '*' && next == '/') {
                        visibleSql.append(' ');
                        index++;
                        state = ScanState.NORMAL;
                    }
                }
                case STRING_LITERAL -> {
                    visibleSql.append(' ');
                    if (current == '\'' && next == '\'') {
                        visibleSql.append(' ');
                        index++;
                    } else if (current == '\'') {
                        state = ScanState.NORMAL;
                    }
                }
                case QUOTED_IDENTIFIER -> {
                    if (current == identifierQuote) {
                        visibleSql.append(' ');
                        state = ScanState.NORMAL;
                    } else {
                        visibleSql.append(current);
                    }
                }
            }
        }
        return visibleSql.toString();
    }

    private enum ScanState {
        NORMAL,
        STRING_LITERAL,
        QUOTED_IDENTIFIER,
        LINE_COMMENT,
        BLOCK_COMMENT
    }

    public record ValidationResult(
            boolean safe,
            String normalizedSql,
            String riskLevel,
            List<String> errors,
            List<String> warnings,
            Set<String> tables,
            Set<String> sensitiveColumns,
            int appliedLimit) {}
}
