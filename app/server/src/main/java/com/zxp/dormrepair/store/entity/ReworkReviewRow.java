package com.zxp.dormrepair.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zxp.dormrepair.store.DateTimeStringTypeHandler;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("t_rework_review")
public class ReworkReviewRow {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Integer attemptNo;
    private Integer reworkIndex;
    private String status;
    private String requestedBy;
    private String requestReason;
    @TableField(value = "requested_at", typeHandler = DateTimeStringTypeHandler.class)
    private String requestedAt;
    private String decidedBy;
    private String decisionReason;
    @TableField(value = "decided_at", typeHandler = DateTimeStringTypeHandler.class)
    private String decidedAt;
}