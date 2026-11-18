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
@TableName("t_idempotency_record")
public class IdempotencyRecordRow {
    @TableId(type = IdType.INPUT)
    private String id;
    private String actorType;
    private String actorId;
    private String method;
    private String path;
    private String idempotencyKey;
    private String requestHash;
    private String status;
    private Integer responseStatus;
    private String responseBody;
    @TableField(value = "created_at", typeHandler = DateTimeStringTypeHandler.class)
    private String createdAt;
    @TableField(value = "completed_at", typeHandler = DateTimeStringTypeHandler.class)
    private String completedAt;
}
