package com.zxp.dormrepair.domain;

import com.zxp.dormrepair.statemachine.ActorType;

/**
 * 谁在操作。system 是定时任务或自动派工，没有人类。
 */
public record Actor(ActorType type, String id, String name) {

    public static Actor system() {
        return new Actor(ActorType.SYSTEM, "system", "系统");
    }

    public static Actor of(String type, String id, String name) {
        ActorType t = ActorType.of(type);
        String actorId = (id == null || id.isBlank()) ? defaultId(t) : id;
        String actorName = (name == null || name.isBlank()) ? defaultName(t) : name;
        return new Actor(t, actorId, actorName);
    }

    private static String defaultId(ActorType t) {
        return switch (t) {
            case STUDENT -> "S001";
            case WORKER -> "W0428";
            case ADMIN -> "A001";
            case SYSTEM -> "system";
        };
    }

    private static String defaultName(ActorType t) {
        return switch (t) {
            case STUDENT -> "张同学";
            case WORKER -> "李建国";
            case ADMIN -> "王主任";
            case SYSTEM -> "系统";
        };
    }
}
