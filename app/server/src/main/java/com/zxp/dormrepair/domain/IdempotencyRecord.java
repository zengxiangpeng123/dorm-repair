package com.zxp.dormrepair.domain;

/** 一次关键写请求的幂等事实。处理中占位与完成响应共用一行。 */
public record IdempotencyRecord(
        String id,
        String actorType,
        String actorId,
        String method,
        String path,
        String idempotencyKey,
        String requestHash,
        String status,
        Integer responseStatus,
        String responseBody,
        String createdAt,
        String completedAt) {
}
