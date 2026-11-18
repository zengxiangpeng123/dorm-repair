package com.zxp.dormrepair.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zxp.dormrepair.store.DateTimeStringTypeHandler;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("t_ai_review_task")
public class AiReviewTaskRow {
    @TableId(type = IdType.INPUT)
    private String id;
    private String orderNo;
    private Integer orderVersion;
    private String inputHash;
    private String model;
    private String provider;
    private String status;
    private Integer attempts;
    @TableField(value = "requested_at", typeHandler = DateTimeStringTypeHandler.class)
    private String requestedAt;
    @TableField(value = "completed_at", typeHandler = DateTimeStringTypeHandler.class)
    private String completedAt;
    private String errorCode;
    private String errorMessage;
    private String category;
    private String level;
    private Double confidence;
    private String basis;
    private String source;
    private Long latencyMs;
    private Boolean applied;
    @TableField(value = "applied_at", typeHandler = DateTimeStringTypeHandler.class)
    private String appliedAt;
}
