package com.zxp.dormrepair.statemachine;

/**
 * 工单事件。前 12 个是状态机里的合法事件，CANCEL_REQUEST 不改状态、只记录一条申请。
 *
 * <p>最后一个是级联事件：返修单验收通过时，原单从"返修中"直接关闭（SYS-004 第 8 条的附带效果）。
 * 它不在 13 条转移里，也不算进 130 种组合的矩阵，所以 {@link #cascadeOnly()} 单独标出来。
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
    ORIGIN_CLOSED("原单关闭（级联）");

    private final String label;

    OrderEvent(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 会不会改状态。CANCEL_REQUEST 只留一条申请记录，由管理端决定。 */
    public boolean changesStatus() {
        return this != CANCEL_REQUEST;
    }

    /** 是不是只由级联触发、不占 13 条转移名额、也不进矩阵的那种事件。 */
    public boolean cascadeOnly() {
        return this == ORIGIN_CLOSED;
    }
}
