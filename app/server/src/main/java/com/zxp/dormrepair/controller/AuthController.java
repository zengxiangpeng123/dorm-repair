package com.zxp.dormrepair.controller;

import com.zxp.dormrepair.auth.AuthService;
import com.zxp.dormrepair.common.ActorResolver;
import com.zxp.dormrepair.common.ApiResponses;
import com.zxp.dormrepair.dto.LoginRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录 / 登出 / 我是谁。SYS-003 的鉴权段就落在这里。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService auth;
    private final boolean demo;
    private final boolean trustForwardedFor;

    public AuthController(AuthService auth, @Value("${app.demo:true}") boolean demo,
                          @Value("${app.auth.trust-forwarded-for:false}") boolean trustForwardedFor) {
        this.auth = auth;
        this.demo = demo;
        this.trustForwardedFor = trustForwardedFor;
    }

    /** 账号 + 口令换令牌。成功返回 token、过期时间、这个人是谁。 */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest req, HttpServletRequest http) {
        String source = clientAddress(http);
        return ApiResponses.ok(auth.login(req == null ? null : req.account(),
                req == null ? null : req.password(), source));
    }

    /** 登出：令牌立刻作废，再用就是 401。 */
    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest http) {
        boolean killed = auth.logout(ActorResolver.token(http));
        return ApiResponses.ok(Map.of("logout", killed));
    }

    /** 带着令牌问"我是谁"，前端刷新页面时用它恢复身份。 */
    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest http) {
        return ApiResponses.ok(auth.me(ActorResolver.token(http)));
    }

    /** 演示模式专用：给状态机自检台发一张 system 令牌，让 AUTO_ACCEPT / ARCHIVE 记 manual=0。 */
    @PostMapping("/system-token")
    public Map<String, Object> systemToken(HttpServletRequest http) {
        if (!demo) {
            throw com.zxp.dormrepair.common.BizException.notFound("演示模式没开");
        }
        ActorResolver.require(ActorResolver.resolve(http),
                com.zxp.dormrepair.statemachine.ActorType.ADMIN);
        return ApiResponses.ok(auth.systemToken());
    }

    /**
     * 仅在明确信任反向代理时读取 X-Forwarded-For。只取代理追加的最右一跳，
     * 不取客户端可伪造的最左值；代理必须覆盖而不是透传该头。
     */
    public String clientAddress(HttpServletRequest request) {
        if (request == null) return "unknown";
        if (trustForwardedFor) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String[] hops = forwarded.split(",");
                String candidate = hops[hops.length - 1].trim();
                if (isIpAddress(candidate)) {
                    return candidate;
                }
            }
        }
        return request.getRemoteAddr();
    }

    private static boolean isIpAddress(String value) {
        if (value == null || value.isBlank() || value.length() > 45) return false;
        boolean plausibleLiteral = value.matches("\\d{1,3}(?:\\.\\d{1,3}){3}")
                || (value.indexOf(':') >= 0 && value.matches("[0-9a-fA-F:.%]+"));
        if (!plausibleLiteral) return false;
        try {
            // 字面量解析，不触发 DNS；IPv4/IPv6 都接受。
            InetAddress.getByName(value);
            return true;
        } catch (UnknownHostException ex) {
            return false;
        }
    }
}
