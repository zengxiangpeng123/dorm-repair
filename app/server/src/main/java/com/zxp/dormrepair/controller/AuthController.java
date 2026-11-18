package com.zxp.dormrepair.controller;

import com.zxp.dormrepair.auth.AuthService;
import com.zxp.dormrepair.common.ActorResolver;
import com.zxp.dormrepair.common.ApiResponses;
import com.zxp.dormrepair.dto.LoginRequest;
import jakarta.servlet.http.HttpServletRequest;
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

    public AuthController(AuthService auth, @Value("${app.demo:true}") boolean demo) {
        this.auth = auth;
        this.demo = demo;
    }

    /** 账号 + 口令换令牌。成功返回 token、过期时间、这个人是谁。 */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest req) {
        return ApiResponses.ok(auth.login(req == null ? null : req.account(),
                req == null ? null : req.password()));
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
}
