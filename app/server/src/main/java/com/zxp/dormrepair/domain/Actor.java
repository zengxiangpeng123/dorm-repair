package com.zxp.dormrepair.domain;

import com.zxp.dormrepair.statemachine.ActorType;

/**
 * 谁在操作。system 是定时任务或自动派工，没有人类。
 *
 * <p>身份必须来自已验证的账号或显式构造；不允许缺 id/name 时自动补成某个演示账号。
 */
public record Actor(ActorType type, String id, String name) {

    public Actor {
        if (type == null) {
            throw new IllegalArgumentException("身份类型不能为空");
        }
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("身份账号不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("身份姓名不能为空");
        }
    }

    public static Actor system() {
        return new Actor(ActorType.SYSTEM, "system", "系统");
    }

    /** 仅用于已明确知道角色和账号的受控场景，例如系统内部生成返修子单。 */
    public static Actor of(String type, String id, String name) {
        return new Actor(ActorType.of(type), id, name);
    }
}
