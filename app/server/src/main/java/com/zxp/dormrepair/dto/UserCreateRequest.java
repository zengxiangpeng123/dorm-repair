package com.zxp.dormrepair.dto;

import java.util.List;

/** 管理员创建人员账号；口令只接收不回显。 */
public record UserCreateRequest(
        String id,
        String role,
        String name,
        String team,
        String building,
        String room,
        String phone,
        List<String> buildings,
        String password) {
}
