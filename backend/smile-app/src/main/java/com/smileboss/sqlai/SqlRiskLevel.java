package com.smileboss.sqlai;

/** SQL 风险从低到高分为 L0-L3，L2 需要人工审核，L3 永远拒绝执行。 */
public enum SqlRiskLevel {
    L0,
    L1,
    L2,
    L3;

    public boolean requiresHumanReview() {
        return this == L2;
    }
}
