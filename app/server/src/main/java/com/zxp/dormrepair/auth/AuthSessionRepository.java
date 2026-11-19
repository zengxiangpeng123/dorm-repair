package com.zxp.dormrepair.auth;

import java.util.Optional;

/** 认证会话的持久化契约。数据库只保存令牌摘要，不保存 Bearer 原文。 */
public interface AuthSessionRepository {

    record StoredSession(String tokenHash, String account, String role, String name,
                         String accountId, long issuedAtMs, long expiresAtMs) {
    }

    /** 保存新会话；同一账号只保留最近 maxActive 个活动会话。 */
    void save(String tokenHash, String account, String role, String name,
              String accountId, long issuedAtMs, long expiresAtMs, int maxActive);

    Optional<StoredSession> findActive(String tokenHash, long nowMs);

    void revoke(String tokenHash, long nowMs);

    /** 撤销某账号全部活动会话；用于停用、改密等权限回收。 */
    int revokeAccount(String account, long nowMs);

    int deleteExpired(long nowMs);

    int count();
}
