package com.zxp.dormrepair.auth;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.store", havingValue = "memory", matchIfMissing = true)
public class InMemoryLoginAttemptRepository implements LoginAttemptRepository {
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    @Override
    public Attempt find(String key) {
        return attempts.get(key);
    }

    @Override
    public Attempt increment(String key, int threshold, long blockMillis, long nowMs) {
        return attempts.compute(key, (k, old) -> {
            int failures = (old == null ? 0 : old.failures()) + 1;
            long blocked = failures >= threshold ? nowMs + blockMillis : 0L;
            return new Attempt(failures, blocked);
        });
    }

    @Override
    public void clear(String key) {
        attempts.remove(key);
    }

    @Override
    public int deleteExpired(long nowMs) {
        int before = attempts.size();
        attempts.entrySet().removeIf(e -> e.getValue().blockedUntilMs() > 0
                && e.getValue().blockedUntilMs() <= nowMs);
        return before - attempts.size();
    }
}
