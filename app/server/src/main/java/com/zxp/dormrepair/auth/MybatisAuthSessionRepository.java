package com.zxp.dormrepair.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zxp.dormrepair.store.entity.AuthSessionRow;
import com.zxp.dormrepair.store.mapper.AuthSessionMapper;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.store", havingValue = "mysql")
public class MybatisAuthSessionRepository implements AuthSessionRepository {

    private final AuthSessionMapper mapper;

    public MybatisAuthSessionRepository(AuthSessionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void save(String tokenHash, String account, String role, String name,
                     String accountId, long issuedAtMs, long expiresAtMs, int maxActive) {
        // 同一账号的登录串行化，避免并发登录各自看到“未超限”而突破会话上限。
        Integer locked = mapper.lockAccount(account);
        if (locked == null || locked != 1) {
            throw new IllegalStateException("登录会话并发锁获取失败");
        }
        try {
            AuthSessionRow row = new AuthSessionRow();
            row.setTokenHash(tokenHash);
            row.setAccount(account);
            row.setRole(role);
            row.setName(name);
            row.setAccountId(accountId);
            row.setIssuedAtMs(issuedAtMs);
            row.setExpiresAtMs(expiresAtMs);
            row.setRevokedAtMs(null);
            mapper.insert(row);
            int excess = mapper.countActive(account, issuedAtMs) - Math.max(1, maxActive);
            if (excess > 0) {
                mapper.revokeOldest(account, issuedAtMs, excess);
            }
        } finally {
            mapper.unlockAccount(account);
        }
    }

    @Override
    public Optional<StoredSession> findActive(String tokenHash, long nowMs) {
        AuthSessionRow row = mapper.selectOne(Wrappers.<AuthSessionRow>query()
                .eq("token_hash", tokenHash)
                .isNull("revoked_at_ms")
                .gt("expires_at_ms", nowMs));
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new StoredSession(row.getTokenHash(), row.getAccount(), row.getRole(),
                row.getName(), row.getAccountId(), row.getIssuedAtMs(), row.getExpiresAtMs()));
    }

    @Override
    public void revoke(String tokenHash, long nowMs) {
        mapper.revoke(tokenHash, nowMs);
    }

    @Override
    public int revokeAccount(String account, long nowMs) {
        return mapper.revokeAccount(account, nowMs);
    }

    @Override
    public int deleteExpired(long nowMs) {
        return mapper.deleteExpired(nowMs);
    }

    @Override
    public int count() {
        Long n = mapper.selectCount(null);
        return n == null ? 0 : n.intValue();
    }
}
