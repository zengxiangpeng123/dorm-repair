package com.zxp.dormrepair.dto;

/** 管理员对返修申请做决定：APPROVE / REJECT。 */
public record ReworkReviewRequest(
        Integer version,
        String decision,
        String reason) {
}