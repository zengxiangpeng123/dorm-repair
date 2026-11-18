package com.zxp.dormrepair.file;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 内存模式的字节存储，仅用于单元测试；生产必须使用本地或 S3 私有存储。 */
@Component
@ConditionalOnProperty(name = "app.file.storage", havingValue = "memory")
public class InMemoryFileStorage implements FileStorage {

    private final ConcurrentHashMap<String, byte[]> objects = new ConcurrentHashMap<>();

    @Override
    public void put(String objectKey, java.io.InputStream input, long contentLength, String contentType) {
        try {
            objects.put(objectKey, input.readAllBytes());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("内存文件写入失败", e);
        }
    }

    @Override
    public Optional<StoredObject> open(String objectKey) {
        byte[] bytes = objects.get(objectKey);
        return bytes == null ? Optional.empty() : Optional.of(
                new StoredObject(new java.io.ByteArrayInputStream(bytes), bytes.length, "application/octet-stream"));
    }

    @Override
    public void delete(String objectKey) {
        objects.remove(objectKey);
    }
}
