package com.zxp.dormrepair;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zxp.dormrepair.file.FileStorage;
import com.zxp.dormrepair.file.FileStorageHealthIndicator;
import java.io.InputStream;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

class FileStorageHealthIndicatorTest {

    @Test
    @DisplayName("存储不可访问时就绪检查必须失败")
    void reportsDownWhenStorageCheckFails() {
        FileStorage storage = new FileStorage() {
            @Override
            public void put(String objectKey, InputStream input, long contentLength, String contentType) {
            }

            @Override
            public boolean putIfAbsent(String objectKey, InputStream input, long contentLength, String contentType) {
                return true;
            }

            @Override
            public Optional<StoredObject> open(String objectKey) {
                return Optional.empty();
            }

            @Override
            public void delete(String objectKey) {
            }

            @Override
            public void checkHealth() {
                throw new IllegalStateException("bucket unavailable");
            }
        };

        var health = new FileStorageHealthIndicator(storage).health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("IllegalStateException", health.getDetails().get("error"));
    }

    @Test
    @DisplayName("存储可访问时就绪检查通过")
    void reportsUpWhenStorageCheckSucceeds() {
        FileStorage storage = new FileStorage() {
            @Override
            public void put(String objectKey, InputStream input, long contentLength, String contentType) {
            }

            @Override
            public boolean putIfAbsent(String objectKey, InputStream input, long contentLength, String contentType) {
                return true;
            }

            @Override
            public Optional<StoredObject> open(String objectKey) {
                return Optional.empty();
            }

            @Override
            public void delete(String objectKey) {
            }
        };

        var health = new FileStorageHealthIndicator(storage).health();

        assertEquals(Status.UP, health.getStatus());
    }
}
