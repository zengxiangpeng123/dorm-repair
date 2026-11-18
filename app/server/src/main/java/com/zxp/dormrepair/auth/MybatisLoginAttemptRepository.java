package com.zxp.dormrepair.auth;

import com.zxp.dormrepair.store.entity.LoginAttemptRow;
import com.zxp.dormrepair.store.mapper.LoginAttemptMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.store", havingValue = "mysql")
public class MybatisLoginAttemptRepository implements LoginAttemptRepository {
    private final LoginAttemptMapper mapper;

    public MybatisLoginAttemptRepository(LoginAttemptMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Attempt find(String key) {
        LoginAttemptRow row = mapper.find(key);
        return row == null ? null : new Attempt(
                row.getFailures() == null ? 0 : row.getFailures(),
                row.getBlockedUntilMs() == null ? 0L : row.getBlockedUntilMs());
    }

    @Override
    public Attempt increment(String key, int threshold, long blockMillis, long nowMs) {
        LoginAttemptRow row = mapper.find(key);
        if (row == null) {
            row = new LoginAttemptRow();
            row.setAttemptKey(key);
            row.setFailures(1);
            row.setBlockedUntilMs(threshold <= 1 ? nowMs + blockMillis : 0L);
            row.setUpdatedAtMs(nowMs);
            mapper.insert(row);
        } else {
            mapper.increment(key, threshold, nowMs + blockMillis, nowMs);
            row = mapper.find(key);
        }
        return new Attempt(row.getFailures() == null ? 0 : row.getFailures(),
                row.getBlockedUntilMs() == null ? 0L : row.getBlockedUntilMs());
    }

    @Override
    public void clear(String key) {
        mapper.clear(key);
    }

    @Override
    public int deleteExpired(long nowMs) {
        return mapper.deleteExpired(nowMs, nowMs - 24 * 3600_000L);
    }
}
