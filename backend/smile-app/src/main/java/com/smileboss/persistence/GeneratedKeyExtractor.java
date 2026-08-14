package com.smileboss.persistence;

import com.smileboss.common.BizException;
import org.springframework.jdbc.support.KeyHolder;

import java.util.List;
import java.util.Map;

/** 兼容 MySQL 与 H2 不同的 generated keys 返回结构。 */
public final class GeneratedKeyExtractor {
    private GeneratedKeyExtractor() {
        throw new IllegalStateException("Utility class");
    }

    public static long extractId(KeyHolder keyHolder) {
        Map<String, Object> keys = keyHolder.getKeys();
        Object directId = findId(keys);
        if (directId instanceof Number number) {
            return number.longValue();
        }

        List<Map<String, Object>> keyRows = keyHolder.getKeyList();
        for (Map<String, Object> keyRow : keyRows) {
            Object rowId = findId(keyRow);
            if (rowId instanceof Number number) {
                return number.longValue();
            }
        }
        throw new BizException("无法取得新增记录编号");
    }

    private static Object findId(Map<String, Object> values) {
        if (values == null) {
            return null;
        }
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if ("id".equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }
}
