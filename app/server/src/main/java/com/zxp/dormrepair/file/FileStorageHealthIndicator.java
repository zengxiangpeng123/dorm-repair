package com.zxp.dormrepair.file;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** 就绪探针必须证明文件存储可用，避免进程已启动但照片上传必然失败。 */
@Component
public class FileStorageHealthIndicator implements HealthIndicator {

    private final FileStorage storage;

    public FileStorageHealthIndicator(FileStorage storage) {
        this.storage = storage;
    }

    @Override
    public Health health() {
        try {
            storage.checkHealth();
            return Health.up().withDetail("type", storage.type()).build();
        } catch (RuntimeException e) {
            return Health.down()
                    .withDetail("type", storage.type())
                    .withDetail("error", e.getClass().getSimpleName())
                    .build();
        }
    }
}
