package com.zxp.dormrepair.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 人员表 t_user。{@code created_at} 交给数据库默认值，这里不映射。
 */
@Getter
@Setter
@TableName("t_user")
public class UserRow {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String role;
    private String name;
    private String team;

    @TableField("building_id")
    private String buildingId;

    private String room;
    private String phone;
    private Boolean active;

    /** {@code pbkdf2$...}，见 PasswordHasher。NULL 表示还没设口令。 */
    @TableField("password_hash")
    private String password;
}
