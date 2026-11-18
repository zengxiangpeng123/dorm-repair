package com.zxp.dormrepair.statemachine;

/**
 * 工单九状态。名字与 SYS-004 第五条一一对应，不许增删。
 */
public enum OrderStatus {

    PENDING("待派工"),
    ASSIGNED("已派工"),
    REPAIRING("维修中"),
    PARTS("待配件"),
    VERIFYING("待验收"),
    REWORKING("返修中"),
    DONE("已完成"),
    CLOSED("已关闭"),
    CANCELLED("已取消");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean terminal() {
        return this == CLOSED || this == CANCELLED;
    }

    /** 在办：还没归档、也没作废。 */
    public boolean active() {
        return !terminal();
    }
}
