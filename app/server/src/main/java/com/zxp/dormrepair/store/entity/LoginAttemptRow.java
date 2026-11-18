package com.zxp.dormrepair.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("t_login_attempt")
public class LoginAttemptRow {
    @TableId(value = "attempt_key", type = IdType.INPUT)
    private String attemptKey;
    private Integer failures;
    private Long blockedUntilMs;
    private Long updatedAtMs;
}
