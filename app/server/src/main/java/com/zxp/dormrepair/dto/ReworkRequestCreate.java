package com.zxp.dormrepair.dto;

/** 学生提交返修申请。 */
public record ReworkRequestCreate(
        Integer version,
        String reason) {
}