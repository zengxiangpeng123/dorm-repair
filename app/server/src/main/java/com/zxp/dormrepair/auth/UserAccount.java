package com.zxp.dormrepair.auth;

import com.zxp.dormrepair.domain.Actor;
import com.zxp.dormrepair.statemachine.ActorType;

/**
 * 一张人员表。登录成功之后就是靠它造出 {@link Actor}。
 *
 * @param id       学号 / 工号，同时也是账号
 * @param role     STUDENT / WORKER / ADMIN
 * @param password PBKDF2 串，见 {@link PasswordHasher}
 */
public record UserAccount(String id,
                          ActorType role,
                          String name,
                          String team,
                          String building,
                          String room,
                          String phone,
                          boolean active,
                          String password) {

    public Actor toActor() {
        return new Actor(role, id, name);
    }

    /** 出参用，绝不带 password。 */
    public java.util.Map<String, Object> view() {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", id);
        m.put("role", role.name());
        m.put("name", name);
        if (team != null) {
            m.put("team", team);
        }
        if (building != null) {
            m.put("building", building);
        }
        if (room != null) {
            m.put("room", room);
        }
        return m;
    }
}
