package com.zxp.dormrepair.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.zxp.dormrepair.store.ScheduledTaskLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** 定期清掉已过期和已撤销的会话，避免认证表无界增长。 */
@Service
public class AuthMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(AuthMaintenanceService.class);
    private final TokenStore tokens;
    private final ScheduledTaskLock taskLock;

    public AuthMaintenanceService(TokenStore tokens, ScheduledTaskLock taskLock) {
        this.tokens = tokens;
        this.taskLock = taskLock;
    }

    @Scheduled(fixedDelayString = "${app.auth.session-cleanup-minutes:60}000",
            initialDelay = 60_000L)
    public void cleanup() {
        try {
            boolean ran = taskLock.runExclusive("auth-session-cleanup", 0, () -> {
                int deleted = tokens.deleteExpired();
                if (deleted > 0) {
                    log.info("认证会话清理完成：删除 {} 条", deleted);
                }
            });
            if (!ran) {
                log.debug("认证会话清理：其他实例正在执行，本轮跳过");
            }
        } catch (RuntimeException e) {
            log.warn("认证会话清理失败：{}", e.getMessage());
        }
    }
}
