package com.zxp.dormrepair.domain;

/**
 * 一次后台 AI 复核的事实记录。状态和工单字段分开，失败/晚到不会伪造业务结果。
 */
public record AiReviewTask(
        String id,
        String orderNo,
        int orderVersion,
        String inputHash,
        String model,
        String provider,
        AiReviewTaskStatus status,
        int attempts,
        String requestedAt,
        String completedAt,
        String errorCode,
        String errorMessage,
        String category,
        String level,
        Double confidence,
        String basis,
        String source,
        long latencyMs,
        boolean applied,
        String appliedAt) {
}
