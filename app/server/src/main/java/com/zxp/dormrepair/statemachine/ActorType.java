package com.zxp.dormrepair.statemachine;

/**
 * 谁在推这条事件。system 没有人类，事件记 manual=0。
 */
public enum ActorType {

    STUDENT,
    WORKER,
    ADMIN,
    SYSTEM;

    public static ActorType of(String raw) {
        if (raw == null || raw.isBlank()) {
            return STUDENT;
        }
        for (ActorType t : values()) {
            if (t.name().equalsIgnoreCase(raw.trim())) {
                return t;
            }
        }
        return STUDENT;
    }

    /** system 的动作不算人工介入。 */
    public int manual() {
        return this == SYSTEM ? 0 : 1;
    }
}
