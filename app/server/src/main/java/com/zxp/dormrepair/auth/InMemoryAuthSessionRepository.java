package com.zxp.dormrepair.auth;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.store", havingValue = "memory", matchIfMissing = true)
public class InMemoryAuthSessionRepository implements AuthSessionRepository {

    private record Row(StoredSession session, long revokedAtMs) {
    }

    private final Map<String, Row> rows = new ConcurrentHashMap<>();

    @Override
    public void save(String tokenHash, String account, String role, String name,
                     String accountId, long issuedAtMs, long expiresAtMs) {
        rows.put(tokenHash, new Row(new StoredSession(tokenHash, account, role, name,
                accountId, issuedAtMs, expiresAtMs), 0L));
    }

    @Override
    public Optional<StoredSession> findActive(String tokenHash, long nowMs) {
        Row row = rows.get(tokenHash);
        if (row == null || row.revokedAtMs() > 0 || row.session().expiresAtMs() <= nowMs) {
            return Optional.empty();
        }
        return Optional.of(row.session());
    }

    @Override
    public void revoke(String tokenHash, long nowMs) {
        Row row = rows.get(tokenHash);
        if (row != null) {
            rows.put(tokenHash, new Row(row.session(), nowMs));
        }
    }

    @Override
    public int deleteExpired(long nowMs) {
        int before = rows.size();
        rows.entrySet().removeIf(e -> e.getValue().revokedAtMs() > 0
                || e.getValue().session().expiresAtMs() <= nowMs);
        return before - rows.size();
    }

    @Override
    public int count() {
        return rows.size();
    }
}
