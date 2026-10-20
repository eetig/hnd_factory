package org.example.audit;

import lombok.Builder;

/**
 * 一条待落库的留痕（变更-129）。用 record 而不是实体：调用方只负责"描述发生了什么"，
 * 落库由 {@link AuditRecorder} 异步做，两者不共享可变状态。
 */
@Builder
public record AuditRecord(
        String event,
        String username,
        String ip,
        String userAgent,
        String method,
        String path,
        String target,
        String paramsDigest,
        String result) {

    /** 参数摘要：只留 16 位，够区分"同一个参数集"就行，避免长串占地方 */
    public static String digest(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}