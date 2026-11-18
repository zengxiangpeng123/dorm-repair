package com.zxp.dormrepair.domain;

/** 需要人工跟进的例外记录。 */
public record ExceptionRecord(
        long id,
        String kind,
        String orderNo,
        String detail,
        String status,
        String handledBy,
        String createdAt,
        String closedAt) {
}
