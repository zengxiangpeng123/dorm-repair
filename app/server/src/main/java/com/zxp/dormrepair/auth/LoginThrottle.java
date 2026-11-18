package com.zxp.dormrepair.auth;

import com.zxp.dormrepair.common.BizException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 进程内登录失败限流。按账号与来源组合计数，成功登录后清零。 */
@Component
public class LoginThrottle {

    private record Attempt(int failures, long blockedUntil) {
    }

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();
    private final int maxFailures;
    private final long blockMillis;

    public LoginThrottle(@Value("${app.auth.login-max-failures:5}") int maxFailures,
                         @Value("${app.auth.login-block-minutes:15}") int blockMinutes) {
        this.maxFailures = Math.max(1, maxFailures);
        this.blockMillis = Math.max(1, blockMinutes) * 60_000L;
    }

    public void check(String account, String source) {
        String key = key(account, source);
        Attempt attempt = attempts.get(key);
        if (attempt != null && attempt.blockedUntil() > System.currentTimeMillis()) {
            throw new BizException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                    "LOGIN_RATE_LIMITED", "登录失败次数过多，请稍后再试");
        }
    }

    public void failure(String account, String source) {
        String key = key(account, source);
        attempts.compute(key, (k, old) -> {
            int failures = (old == null ? 0 : old.failures()) + 1;
            long blocked = failures >= maxFailures ? System.currentTimeMillis() + blockMillis : 0L;
            return new Attempt(failures, blocked);
        });
    }

    public void success(String account, String source) {
        attempts.remove(key(account, source));
    }

    public void sweep() {
        long now = System.currentTimeMillis();
        attempts.entrySet().removeIf(e -> e.getValue().blockedUntil() > 0
                && e.getValue().blockedUntil() <= now);
    }

    private static String key(String account, String source) {
        return (account == null ? "" : account.trim().toLowerCase()) + "|" + (source == null ? "" : source);
    }
}
