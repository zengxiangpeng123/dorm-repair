package com.zxp.dormrepair.statemachine;

/**
 * 谁在推这条事件。system 没有人类，事件记 manual=0。
 */
public enum ActorType {

    STUDENT,
    WORKER,
    ADMIN,
    SYSTEM;

    /**
     * 严格解析。身份不能因为参数缺失或拼错就退化成学生，否则会形成隐式提权。
     */
    public static ActorType of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("角色不能为空");
        }
        for (ActorType t : values()) {
            if (t.name().equalsIgnoreCase(raw.trim())) {
                return t;
            }
        }
        throw new IllegalArgumentException("未知角色：" + raw);
    }

    /** system 的动作不算人工介入。 */
    public int manual() {
        return this == SYSTEM ? 0 : 1;
    }
}
