package com.zxp.dormrepair.domain;

import com.zxp.dormrepair.statemachine.OrderStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 工单。M1 阶段先放内存里，M2 换成 MySQL 表时字段一个不改。
 *
 * <p>规矩 C-07：任何字段的改动都要和状态变更写在同一次 update 里，别分两笔。
 */
@Getter
@Setter
public class WorkOrder {

    private String no;
    private OrderStatus status;
    private int version;

    private String building;
    private int floor;
    private String room;
    private String studentId;
    private String studentName;

    private String desc;
    private String window;
    private List<String> photosBefore = new ArrayList<>();
    private List<String> photosAfter = new ArrayList<>();

    private String category;
    private String level;
    private String levelSource;
    private String aiBasis;

    private String workerId;
    private String workerName;

    private String partsName;
    private BigDecimal partsAmount;
    private String partsEta;

    private String createdAt;
    private String assignedAt;
    private String arriveAt;
    private String finishAt;
    private String signAt;
    private String closedAt;
    private String cancelledAt;
    private String statusAt;

    private List<OrderEventLog> events = new ArrayList<>();

    /** 位置的人话写法，前端表里直接用。 */
    public String location() {
        return building + " " + floor + "F-" + room;
    }
}
