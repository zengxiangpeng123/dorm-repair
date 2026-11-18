package com.zxp.dormrepair.auth;

import com.zxp.dormrepair.common.BizException;
import com.zxp.dormrepair.common.TimeUtil;
import com.zxp.dormrepair.domain.Actor;
import com.zxp.dormrepair.statemachine.ActorType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 登录、登出、当前身份。所有鉴权逻辑只在这里和 {@link AuthFilter} 里。
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final TokenStore tokens;
    private final LoginThrottle throttle;
    private final long ttlMillis;

    public AuthService(UserRepository users,
                       TokenStore tokens,
                       LoginThrottle throttle,
                       @Value("${app.auth.ttl-hours:8}") double ttlHours) {
        this.users = users;
        this.tokens = tokens;
        this.throttle = throttle;
        this.ttlMillis = (long) (ttlHours * 3600_000L);
    }

    /**
     * 账号 + 口令换令牌。账号不存在、口令不对、账号停用，一律 401 且话术一样，
     * 不告诉调用方"是账号错了还是密码错了"。
     */
    public Map<String, Object> login(String account, String password) {
        return login(account, password, "unknown");
    }

    public Map<String, Object> login(String account, String password, String source) {
        if (account == null || account.isBlank() || password == null || password.isEmpty()) {
            throw BizException.badRequest("账号和口令都要填");
        }
        String normalized = account.trim();
        throttle.check(normalized, source);
        Optional<UserAccount> found = users.find(normalized);
        if (found.isEmpty()) {
            throttle.failure(normalized, source);
            throw BizException.unauthorized("账号或口令不对");
        }
        UserAccount u = found.get();
        if (!PasswordHasher.verify(password, u.password())) {
            throttle.failure(normalized, source);
            throw BizException.unauthorized("账号或口令不对");
        }
        if (!u.active()) {
            throttle.failure(normalized, source);
            throw BizException.forbidden("这个账号已停用，找后勤管理员");
        }
        throttle.success(normalized, source);
        TokenStore.Session s = tokens.issue(u.toActor(), u.id(), ttlMillis);
        return body(s, u);
    }

    /** 演示用：给自检台发一张 system 令牌，这样 AUTO_ACCEPT / ARCHIVE 仍记 manual=0。 */
    public Map<String, Object> systemToken() {
        Actor sys = new Actor(ActorType.SYSTEM, "system", "定时任务");
        TokenStore.Session s = tokens.issue(sys, "system", 3600_000L);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("token", s.token());
        m.put("expiresAt", TimeUtil.fmt(s.expiresAt()));
        m.put("user", Map.of("id", "system", "role", "SYSTEM", "name", "定时任务"));
        m.put("actor", Map.of("type", "SYSTEM", "id", "system", "name", "定时任务"));
        m.put("ttlSeconds", 3600);
        return m;
    }

    public TokenStore.Session require(String token) {
        TokenStore.Session s = tokens.resolve(token);
        if (s == null) {
            throw BizException.unauthorized("登录已失效，请重新登录");
        }
        return s;
    }

    public Map<String, Object> me(String token) {
        TokenStore.Session s = require(token);
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", s.actor().id());
        user.put("role", s.actor().type().name());
        user.put("name", s.actor().name());
        users.find(s.account()).ifPresent(u -> {
            if (u.team() != null) {
                user.put("team", u.team());
            }
            if (u.building() != null) {
                user.put("building", u.building());
            }
            if (u.room() != null) {
                user.put("room", u.room());
            }
        });
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("user", user);
        m.put("expiresAt", TimeUtil.fmt(s.expiresAt()));
        return m;
    }

    public boolean logout(String token) {
        return tokens.revoke(token);
    }

    private Map<String, Object> body(TokenStore.Session s, UserAccount u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("token", s.token());
        m.put("expiresAt", TimeUtil.fmt(s.expiresAt()));
        m.put("ttlSeconds", ttlMillis / 1000);
        m.put("user", u.view());
        return m;
    }
}
