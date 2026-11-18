package com.zxp.dormrepair.statemachine;

/**
 * 工单事件。状态事件之外还有日志型事件与级联事件。
 *
 * <p>CANCEL_REQUEST、REWORK_REQUEST、REWORK_REJECTED 只写时间线，不改变状态。
 * ORIGIN_CLOSED 是返修单验收通过后的级联副作用。这三类都不占 13 条状态转移。
 */
public enum OrderEvent {

    SUBMIT("提交报修"),
    ASSIGN("派工"),
    ARRIVE("到门打卡"),
    NO_SHOW("到访未遇"),
    FINISH("完工提交"),
    NEED_PARTS("登记缺件"),
    PARTS_ARRIVED("配件到货"),
    ACCEPT("验收通过"),
    REJECT("验收不通过"),
    AUTO_ACCEPT("超时自动验收"),
    ARCHIVE("归档"),
    CANCEL("撤销报修"),
    CANCEL_REQUEST("申请撤销"),
    REWORK_REQUEST("申请返修"),
    REWORK_REJECTED("驳回返修申请"),
    ORIGIN_CLOSED("原单关闭（级联）");

    private final String label;

    OrderEvent(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 会不会改状态。申请类事件只留记录。 */
    public boolean changesStatus() {
        return this != CANCEL_REQUEST && this != REWORK_REQUEST && this != REWORK_REJECTED;
    }

    /** 是不是只由级联触发、不占 13 条转移名额、也不进矩阵的那种事件。 */
    public boolean cascadeOnly() {
        return this == ORIGIN_CLOSED;
    }

    /** 日志型事件：不参与转移矩阵，但需要身份与时间线。 */
    public boolean auditOnly() {
        return this == CANCEL_REQUEST || this == REWORK_REQUEST || this == REWORK_REJECTED;
    }
}
