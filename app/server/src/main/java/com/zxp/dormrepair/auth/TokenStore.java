package com.zxp.dormrepair.auth;

import com.zxp.dormrepair.domain.Actor;
import com.zxp.dormrepair.statemachine.ActorType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 认证会话服务。原始 Bearer 只返回给客户端，数据库仅保存 SHA-256 摘要；
 * 会话可跨进程重启恢复，也可在数据库中即时撤销。
 */
@Component
public class TokenStore {

    public record Session(String token, Actor actor, String account, long issuedAt, long expiresAt) {
        public boolean expired(long now) {
            return now >= expiresAt;
        }
    }

    private static final SecureRandom RANDOM = new SecureRandom();
    private final AuthSessionRepository sessions;
    private final UserRepository users;
    private final int maxActiveSessions;

    @org.springframework.beans.factory.annotation.Autowired
    public TokenStore(AuthSessionRepository sessions, UserRepository users,
                      @org.springframework.beans.factory.annotation.Value("${app.auth.max-active-sessions:5}") int maxActiveSessions) {
        this.sessions = sessions;
        this.users = users;
        this.maxActiveSessions = Math.max(1, maxActiveSessions);
    }

    public TokenStore(AuthSessionRepository sessions, UserRepository users) {
        this(sessions, users, 5);
    }

    public Session issue(Actor actor, String account, long ttlMillis) {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        long now = System.currentTimeMillis();
        long expiresAt = now + ttlMillis;
        sessions.save(hash(token), account, actor.type().name(), actor.name(),
                actor.id(), now, expiresAt, maxActiveSessions);
        return new Session(token, actor, account, now, expiresAt);
    }

    public Session resolve(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        Optional<AuthSessionRepository.StoredSession> found = sessions.findActive(hash(token),
                System.currentTimeMillis());
        if (found.isEmpty()) {
            return null;
        }
        AuthSessionRepository.StoredSession s = found.get();
        // 权限回收立即生效：普通人员账号停用后旧会话失效；SYSTEM 是内部任务身份。
        if (!"SYSTEM".equals(s.role())
                && users.find(s.account()).map(u -> !u.active()).orElse(true)) {
            return null;
        }
        Actor actor = new Actor(ActorType.of(s.role()), s.accountId(), s.name());
        return new Session(token, actor, s.account(), s.issuedAtMs(), s.expiresAtMs());
    }

    public boolean revoke(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        long now = System.currentTimeMillis();
        Session active = resolve(token);
        if (active == null) {
            return false;
        }
        sessions.revoke(hash(token), now);
        return true;
    }

    public int deleteExpired() {
        return sessions.deleteExpired(System.currentTimeMillis());
    }

    public int revokeAccount(String account) {
        return sessions.revokeAccount(account, System.currentTimeMillis());
    }

    public int size() {
        return sessions.count();
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
