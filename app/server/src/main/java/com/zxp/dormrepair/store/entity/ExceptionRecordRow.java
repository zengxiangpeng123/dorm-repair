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
@TableName("t_exception_record")
public class ExceptionRecordRow {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String kind;
    private String orderNo;
    private String detail;
    private String status;
    private String handledBy;

    @TableField(value = "created_at", typeHandler = DateTimeStringTypeHandler.class)
    private String createdAt;

    @TableField(value = "closed_at", typeHandler = DateTimeStringTypeHandler.class)
    private String closedAt;
}
