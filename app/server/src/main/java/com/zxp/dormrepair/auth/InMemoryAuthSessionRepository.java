package com.zxp.dormrepair.auth;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.store", havingValue = "memory", matchIfMissing = true)
public class InMemoryAuthSessionRepository implements AuthSessionRepository {

    private record Row(StoredSession session, long revokedAtMs) {
    }

    private final Map<String, Row> rows = new ConcurrentHashMap<>();
    private final Map<String, Long> sequence = new ConcurrentHashMap<>();
    private final AtomicLong nextSequence = new AtomicLong();

    @Override
    public void save(String tokenHash, String account, String role, String name,
                     String accountId, long issuedAtMs, long expiresAtMs, int maxActive) {
        synchronized (this) {
            rows.put(tokenHash, new Row(new StoredSession(tokenHash, account, role, name,
                    accountId, issuedAtMs, expiresAtMs), 0L));
            sequence.put(tokenHash, nextSequence.incrementAndGet());
            var active = rows.entrySet().stream()
                    .filter(e -> account.equals(e.getValue().session().account()))
                    .filter(e -> e.getValue().revokedAtMs() == 0
                            && e.getValue().session().expiresAtMs() > issuedAtMs)
                    .sorted(Comparator.comparingLong(e -> sequence.getOrDefault(e.getKey(), 0L)))
                    .toList();
            int excess = active.size() - Math.max(1, maxActive);
            for (int i = 0; i < excess; i++) {
                String old = active.get(i).getKey();
                Row row = rows.get(old);
                if (row != null) rows.put(old, new Row(row.session(), issuedAtMs));
                sequence.remove(old);
            }
        }
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
    public int revokeAccount(String account, long nowMs) {
        int changed = 0;
        for (var entry : rows.entrySet()) {
            Row row = entry.getValue();
            if (account.equals(row.session().account()) && row.revokedAtMs() == 0
                    && row.session().expiresAtMs() > nowMs) {
                rows.put(entry.getKey(), new Row(row.session(), nowMs));
                changed++;
            }
        }
        return changed;
    }

    @Override
    public int deleteExpired(long nowMs) {
        int before = rows.size();
        rows.entrySet().removeIf(e -> {
            boolean remove = e.getValue().revokedAtMs() > 0
                    || e.getValue().session().expiresAtMs() <= nowMs;
            if (remove) sequence.remove(e.getKey());
            return remove;
        });
        return before - rows.size();
    }

    @Override
    public int count() {
        return rows.size();
    }
}
