package com.zxp.dormrepair.dto;

/**
 * 登录请求。{@code account} 是学号 / 工号，也是账号。
 */
public record LoginRequest(String account, String password) {
}
