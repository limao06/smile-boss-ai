package com.smileboss.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 摘要工具；统一文件去重、SQL 快照和工作流审核快照的计算方式。 */
public final class HashUtils {
    private static final String SHA_256 = "SHA-256";

    private HashUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance(SHA_256).digest(value);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 Java 运行时不支持 SHA-256", exception);
        }
    }
}
