package com.smileboss.sqlai;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SqlSafetyGuardTest {
    private final SqlSafetyGuard guard = new SqlSafetyGuard();
    private final Set<String> allowed = Set.of("talent_candidate", "recruit_job");

    @Test
    void blocksStackedStatementsAndWriteOperations() {
        SqlSafetyGuard.ValidationResult result = guard.validate(
                "SELECT id FROM talent_candidate; DELETE FROM talent_candidate", allowed, 200);
        assertFalse(result.safe());
        assertEquals("L3", result.riskLevel());
        assertTrue(result.errors().stream().anyMatch(it -> it.contains("一条SQL") || it.contains("DELETE")));
    }

    @Test
    void blocksUnknownTables() {
        SqlSafetyGuard.ValidationResult result = guard.validate("SELECT * FROM sys_user", allowed, 200);
        assertFalse(result.safe());
        assertTrue(result.errors().stream().anyMatch(it -> it.contains("未授权")));
    }

    @Test
    void blocksUnknownTableInCommaJoin() {
        SqlSafetyGuard.ValidationResult result = guard.validate(
                "SELECT c.id FROM talent_candidate c, sys_user u WHERE c.id=u.id", allowed, 200);
        assertFalse(result.safe());
        assertTrue(result.errors().stream().anyMatch(it -> it.contains("SYS_USER")));
    }

    @Test
    void addsLimitAndRaisesSensitiveColumnsToL2() {
        SqlSafetyGuard.ValidationResult result = guard.validate(
                "SELECT name, phone FROM talent_candidate ORDER BY id", allowed, 50);
        assertTrue(result.safe());
        assertEquals("L2", result.riskLevel());
        assertTrue(result.normalizedSql().endsWith("LIMIT 50"));
        assertTrue(result.sensitiveColumns().contains("phone"));
    }

    @Test
    void ignoresDangerousWordsInsideStringLiterals() {
        SqlSafetyGuard.ValidationResult result = guard.validate(
                "SELECT COUNT(*) AS delete_count FROM talent_candidate WHERE name='DELETE FROM recruit_job'", allowed, 20);
        assertTrue(result.safe());
    }
}
