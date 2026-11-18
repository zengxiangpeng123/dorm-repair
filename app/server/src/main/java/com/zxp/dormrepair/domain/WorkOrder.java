package com.zxp.dormrepair.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zxp.dormrepair.statemachine.OrderStatus;
import com.zxp.dormrepair.store.DateTimeStringTypeHandler;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 工单。M1 放内存、M2 起落 MySQL，字段一个不改 —— 类上这套注解就是映射规则。
 *
 * <p>规矩 C-07：任何字段的改动都要和状态变更写在同一次 update 里，别分两笔。
 *
 * <p>不上表的三个字段（events / 两张照片列表）标 exist=false：
 * 照片在 t_work_order_photo 里，事件在 t_work_order_event 里，都不是工单表的列。
 */
@Getter
@Setter
@TableName("t_work_order")
public class WorkOrder {

    @TableId(value = "no", type = IdType.INPUT)
    private String no;

    /** EnumTypeHandler 存 name()，库里的值就是 PENDING / ASSIGNED 这些。 */
    private OrderStatus status;

    private int version;

    @TableField("building_id")
    private String building;

    private int floor;
    private String room;
    private String studentId;
    private String studentName;

    private String description;

    private String visitWindow;

    @TableField(exist = false)
    private List<String> photosBefore = new ArrayList<>();

    @TableField(exist = false)
    private List<String> photosAfter = new ArrayList<>();

    private String category;
    private String level;
    private String levelSource;
    private String aiBasis;

    private String workerId;
    private String workerName;

    private String partsName;
    private BigDecimal partsAmount;

    @TableField(value = "parts_eta", typeHandler = DateTimeStringTypeHandler.class)
    private String partsEta;

    @TableField(value = "created_at", typeHandler = DateTimeStringTypeHandler.class)
    private String createdAt;

    @TableField(value = "assigned_at", typeHandler = DateTimeStringTypeHandler.class)
    private String assignedAt;

    @TableField(value = "arrive_at", typeHandler = DateTimeStringTypeHandler.class)
    private String arriveAt;

    @TableField(value = "finish_at", typeHandler = DateTimeStringTypeHandler.class)
    private String finishAt;

    @TableField(value = "sign_at", typeHandler = DateTimeStringTypeHandler.class)
    private String signAt;

    @TableField(value = "closed_at", typeHandler = DateTimeStringTypeHandler.class)
    private String closedAt;

    @TableField(value = "cancelled_at", typeHandler = DateTimeStringTypeHandler.class)
    private String cancelledAt;

    @TableField(value = "status_at", typeHandler = DateTimeStringTypeHandler.class)
    private String statusAt;

    @TableField(value = "verify_due_at", typeHandler = DateTimeStringTypeHandler.class)
    private String verifyDueAt;

    @TableField(value = "archive_due_at", typeHandler = DateTimeStringTypeHandler.class)
    private String archiveDueAt;

    /** 返修关系：返修单指向原始单（没有 -R 后缀的那个），原单的 reworkCount 由驳回时 +1。 */
    @TableField("rework_of")
    private String reworkOf;

    @TableField("rework_index")
    private Integer reworkIndex;

    @TableField("rework_count")
    private Integer reworkCount = 0;

    @TableField(exist = false)
    private List<OrderEventLog> events = new ArrayList<>();

    /** 位置的人话写法，前端表里直接用。 */
    public String location() {
        return building + " " + floor + "F-" + room;
    }
}
