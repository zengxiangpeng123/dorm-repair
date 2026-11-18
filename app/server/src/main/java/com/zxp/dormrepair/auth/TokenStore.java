package com.zxp.dormrepair.auth;

import com.zxp.dormrepair.domain.Actor;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 令牌表。演示项目存在内存里：重启之后所有人都要重新登录，这是有意为之——
 * 不用为一张会话表再引一次数据库迁移，也不会出现"旧 token 在库里躺三个月"。
 */
@Component
public class TokenStore {

    /**
     * @param account 登录用的账号（system 令牌没有账号，落 "system"）
     */
    public record Session(String token, Actor actor, String account, long issuedAt, long expiresAt) {

        public boolean expired(long now) {
            return now >= expiresAt;
        }
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public Session issue(Actor actor, String account, long ttlMillis) {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        long now = System.currentTimeMillis();
        Session s = new Session(token, actor, account, now, now + ttlMillis);
        sessions.put(token, s);
        return s;
    }

    /** 取会话。过期就顺手删掉，返回 null。 */
    public Session resolve(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        Session s = sessions.get(token);
        if (s == null) {
            return null;
        }
        if (s.expired(System.currentTimeMillis())) {
            sessions.remove(token);
            return null;
        }
        return s;
    }

    public boolean revoke(String token) {
        return token != null && sessions.remove(token) != null;
    }

    public int size() {
        return sessions.size();
    }
}
