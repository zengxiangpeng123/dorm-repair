package com.zxp.dormrepair.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("t_auth_session")
public class AuthSessionRow {

    @TableId(value = "token_hash", type = IdType.INPUT)
    private String tokenHash;

    private String account;
    private String role;
    private String name;
    private String accountId;
    private Long issuedAtMs;
    private Long expiresAtMs;
    private Long revokedAtMs;
}
