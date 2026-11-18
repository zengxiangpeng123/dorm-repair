package com.zxp.dormrepair.auth;

/** 登录失败计数存储；MySQL 实现用于多实例共享限流。 */
public interface LoginAttemptRepository {

    record Attempt(int failures, long blockedUntilMs) {
    }

    Attempt find(String key);

    Attempt increment(String key, int threshold, long blockMillis, long nowMs);

    void clear(String key);

    int deleteExpired(long nowMs);
}
