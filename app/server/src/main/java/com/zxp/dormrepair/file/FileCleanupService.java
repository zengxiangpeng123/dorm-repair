package com.zxp.dormrepair.file;

import com.zxp.dormrepair.common.TimeUtil;
import com.zxp.dormrepair.store.ScheduledTaskLock;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** 清理上传后长期未绑定工单的孤立对象，避免私有桶无限增长。 */
@Service
public class FileCleanupService {

    private static final Logger log = LoggerFactory.getLogger(FileCleanupService.class);

    private final FileRepository repository;
    private final FileStorage storage;
    private final int retentionHours;
    private final int batchSize;
    private final ScheduledTaskLock taskLock;

    @Value("${app.file.cleanup.claim-timeout-minutes:30}")
    private int claimTimeoutMinutes = 30;

    public FileCleanupService(FileRepository repository, FileStorage storage,
                              @Value("${app.file.cleanup.retention-hours:24}") int retentionHours,
                              @Value("${app.file.cleanup.batch-size:100}") int batchSize,
                              ScheduledTaskLock taskLock) {
        this.repository = repository;
        this.storage = storage;
        this.retentionHours = Math.max(1, retentionHours);
        this.batchSize = Math.max(1, batchSize);
        this.taskLock = taskLock;
    }

    /** 每小时执行；手动接口也可调用同一逻辑。 */
    @Scheduled(fixedDelay = 3600000L, initialDelay = 60000L)
    public void tick() {
        try {
            boolean ran = taskLock.runExclusive("file-cleanup", 0, () -> sweep(TimeUtil.now()));
            if (!ran) {
                log.debug("孤儿文件清理：其他实例正在执行，本轮跳过");
            }
        } catch (RuntimeException e) {
            log.warn("孤儿文件清理失败：{}", e.getMessage());
        }
    }

    public Map<String, Object> sweep(String now) {
        long nowMs = TimeUtil.epochMs(now) == null ? TimeUtil.nowMs() : TimeUtil.epochMs(now);
        String cutoff = TimeUtil.fmt(nowMs - retentionHours * 3600_000L);
        String staleBefore = TimeUtil.fmt(nowMs
                - Math.max(1, claimTimeoutMinutes) * 60_000L);
        int recovered = repository.releaseStaleClaims(staleBefore);
        int deleted = 0;
        int failed = 0;
        for (FileRecord file : repository.findUnboundCreatedBefore(cutoff, batchSize)) {
            if (!repository.claimForCleanup(file.id(), now)) {
                continue;
            }
            try {
                storage.delete(file.objectKey());
                repository.delete(file.id());
                deleted++;
            } catch (RuntimeException e) {
                failed++;
                repository.releaseClaim(file.id());
                log.warn("删除孤儿文件失败 id={}：{}", file.id(), e.getMessage());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("at", now);
        result.put("cutoff", cutoff);
        result.put("recoveredClaims", recovered);
        result.put("deleted", deleted);
        result.put("failed", failed);
        return result;
    }
}
