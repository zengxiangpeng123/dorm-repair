package com.zxp.dormrepair.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zxp.dormrepair.store.DateTimeStringTypeHandler;
import lombok.Getter;
import lombok.Setter;

/** t_work_order_event 一行。事件只增不改，所以只用得上 insert 与按单查询。 */
@Getter
@Setter
@TableName("t_work_order_event")
public class OrderEventRow {

    @TableId(value = "seq", type = IdType.AUTO)
    private Long seq;

    private String orderNo;

    @TableField(value = "at", typeHandler = DateTimeStringTypeHandler.class)
    private String at;

    private String event;
    private String actorType;
    private String actorId;
    private String actorName;
    private Integer manual;
    private String note;
}
