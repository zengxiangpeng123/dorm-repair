package com.zxp.dormrepair.domain;

import com.zxp.dormrepair.statemachine.OrderEvent;

/**
 * 一条事件记录。工单的历史就是这张表，任何字段都不许改写它 —— 只能往后追加。
 */
public record OrderEventLog(
        long seq,
        String at,
        OrderEvent event,
        String actorType,
        String actorId,
        String actorName,
        int manual,
        String note) {
}
