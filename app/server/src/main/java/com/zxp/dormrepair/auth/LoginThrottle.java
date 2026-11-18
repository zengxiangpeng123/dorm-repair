package com.zxp.dormrepair.auth;

import com.zxp.dormrepair.common.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 登录失败限流；MySQL 实现跨实例共享，内存实现用于测试。 */
@Component
public class LoginThrottle {

    private final LoginAttemptRepository repository;
    private final int maxFailures;
    private final long blockMillis;
    private final int sourceMaxFailures;

    public LoginThrottle(LoginAttemptRepository repository,
                         @Value("${app.auth.login-max-failures:5}") int maxFailures,
                         @Value("${app.auth.login-block-minutes:15}") int blockMinutes,
                         @Value("${app.auth.login-source-max-failures:20}") int sourceMaxFailures) {
        this.repository = repository;
        this.maxFailures = Math.max(1, maxFailures);
        this.blockMillis = Math.max(1, blockMinutes) * 60_000L;
        this.sourceMaxFailures = Math.max(1, sourceMaxFailures);
    }

    public void check(String account, String source) {
        LoginAttemptRepository.Attempt attempt = repository.find(key(account, source));
        LoginAttemptRepository.Attempt sourceAttempt = repository.find(sourceKey(source));
        if ((attempt != null && attempt.blockedUntilMs() > System.currentTimeMillis())
                || (sourceAttempt != null && sourceAttempt.blockedUntilMs() > System.currentTimeMillis())) {
            throw new BizException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                    "LOGIN_RATE_LIMITED", "登录失败次数过多，请稍后再试");
        }
    }

    public void failure(String account, String source) {
        long now = System.currentTimeMillis();
        repository.increment(key(account, source), maxFailures, blockMillis, now);
        repository.increment(sourceKey(source), sourceMaxFailures, blockMillis, now);
    }

    public void success(String account, String source) {
        repository.clear(key(account, source));
    }

    public void sweep() {
        repository.deleteExpired(System.currentTimeMillis());
    }

    private static String sourceKey(String source) {
        return "ip|" + (source == null ? "" : source);
    }

    private static String key(String account, String source) {
        return (account == null ? "" : account.trim().toLowerCase()) + "|" + (source == null ? "" : source);
    }
}
