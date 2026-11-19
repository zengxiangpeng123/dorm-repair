package com.zxp.dormrepair;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxp.dormrepair.file.S3FileStorage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 真实 S3 兼容联调。默认跳过，避免普通构建依赖 Docker/MinIO；
 * 设置 RUN_S3_STORAGE_TEST=true 且启动测试桶后运行。
 */
class S3FileStorageMySqlTest {

    @Test
    @DisplayName("真实 S3：私有上传、读取、删除与健康检查")
    void privateObjectLifecycle() throws Exception {
        Assumptions.assumeTrue("true".equalsIgnoreCase(System.getenv("RUN_S3_STORAGE_TEST")),
                "set RUN_S3_STORAGE_TEST=true with a local S3-compatible test bucket");
        String endpoint = System.getenv().getOrDefault("S3_TEST_ENDPOINT", "http://127.0.0.1:19000");
        String bucket = System.getenv().getOrDefault("S3_TEST_BUCKET", "dorm-repair-test");
        String access = System.getenv().getOrDefault("S3_TEST_ACCESS_KEY", "dormtest");
        String secret = System.getenv().getOrDefault("S3_TEST_SECRET_KEY", "DormTestOnly-2026");
        var storage = new S3FileStorage(endpoint, "us-east-1", bucket, access, secret, "", true);
        storage.checkHealth();

        String key = "integration/" + System.nanoTime() + ".txt";
        byte[] bytes = "dorm-repair-s3-check".getBytes(StandardCharsets.UTF_8);
        assertTrue(storage.putIfAbsent(key, new ByteArrayInputStream(bytes), bytes.length, "text/plain"));
        assertFalse(storage.putIfAbsent(key, new ByteArrayInputStream(bytes), bytes.length, "text/plain"));
        var found = storage.open(key);
        assertTrue(found.isPresent());
        try (var input = found.get().stream()) {
            assertArrayEquals(bytes, input.readAllBytes());
        }
        storage.delete(key);
        assertTrue(storage.open(key).isEmpty());
    }
}
