package com.zxp.dormrepair.domain;

import java.util.Locale;

/** AI 复核任务状态。数据库和接口统一使用大写枚举名。 */
public enum AiReviewTaskStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FALLBACK,
    FAILED,
    SUPERSEDED;

    public static AiReviewTaskStatus of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("AI 复核状态不能为空");
        }
        return valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
