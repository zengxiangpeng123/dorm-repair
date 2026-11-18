package com.zxp.dormrepair.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 推一条事件时要带的东西。哪个事件用哪几个字段，见 SYS-003 第 5 节，不用的留空即可。
 */
public record TransitRequest(
        Integer version,
        String note,
        String reason,
        String signerName,
        List<String> photosAfter,
        String partsName,
        BigDecimal partsAmount,
        String partsEta,
        String workerId,
        String workerName) {
}
