package com.zxp.dormrepair.auth;

import java.util.Optional;

/** 认证会话的持久化契约。数据库只保存令牌摘要，不保存 Bearer 原文。 */
public interface AuthSessionRepository {

    record StoredSession(String tokenHash, String account, String role, String name,
                         String accountId, long issuedAtMs, long expiresAtMs) {
    }

    void save(String tokenHash, String account, String role, String name,
              String accountId, long issuedAtMs, long expiresAtMs);

    Optional<StoredSession> findActive(String tokenHash, long nowMs);

    void revoke(String tokenHash, long nowMs);

    int deleteExpired(long nowMs);

    int count();
}
