package com.zxp.dormrepair.domain;

/** 一次返修申请及管理员决定。 */
public record ReworkReview(
        long id,
        String orderNo,
        int attemptNo,
        Integer reworkIndex,
        String status,
        String requestedBy,
        String requestReason,
        String requestedAt,
        String decidedBy,
        String decisionReason,
        String decidedAt) {
}