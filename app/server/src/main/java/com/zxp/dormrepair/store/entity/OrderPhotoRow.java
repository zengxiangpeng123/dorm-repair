package com.zxp.dormrepair.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zxp.dormrepair.store.DateTimeStringTypeHandler;
import lombok.Getter;
import lombok.Setter;

/** t_work_order_photo 一行。kind = BEFORE 现场照 / AFTER 完工照。 */
@Getter
@Setter
@TableName("t_work_order_photo")
public class OrderPhotoRow {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String orderNo;
    private String kind;
    private String url;
    private String uploadedBy;

    @TableField(value = "created_at", typeHandler = DateTimeStringTypeHandler.class)
    private String createdAt;
}
