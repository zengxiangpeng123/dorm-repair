package com.zxp.dormrepair.file;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zxp.dormrepair.store.DateTimeStringTypeHandler;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("t_file")
public class FileRecordRow {
    @TableId(type = IdType.INPUT)
    private String id;
    private String objectKey;
    private String originalName;
    private String mime;
    private Long byteSize;
    private String sha256;
    private String uploadedBy;
    private String purpose;
    private String visibility;
    private String orderNo;
    private String kind;
    @TableField(value = "deleted_at", typeHandler = DateTimeStringTypeHandler.class)
    private String deletedAt;
    private String cleanupClaimedAt;
    @TableField(value = "created_at", typeHandler = DateTimeStringTypeHandler.class)
    private String createdAt;
}
