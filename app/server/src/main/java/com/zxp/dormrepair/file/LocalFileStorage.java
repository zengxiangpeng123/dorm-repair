package com.zxp.dormrepair.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 开发环境的本地私有存储；生产必须切换为 S3 兼容对象存储。 */
@Component
@ConditionalOnProperty(name = "app.file.storage", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements FileStorage {

    private final Path root;

    public LocalFileStorage(@Value("${app.file.local-root:./data/uploads}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @Override
    public void put(String objectKey, InputStream input, long contentLength, String contentType) {
        try {
            Path target = resolve(objectKey);
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), ".upload-", ".part");
            try {
                Files.copy(input, temp, StandardCopyOption.REPLACE_EXISTING);
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            throw new IllegalStateException("本地文件写入失败", e);
        }
    }

    @Override
    public Optional<StoredObject> open(String objectKey) {
        try {
            Path file = resolve(objectKey);
            if (!Files.isRegularFile(file)) {
                return Optional.empty();
            }
            String type = Files.probeContentType(file);
            return Optional.of(new StoredObject(Files.newInputStream(file), Files.size(file),
                    type == null ? "application/octet-stream" : type));
        } catch (IOException e) {
            throw new IllegalStateException("本地文件读取失败", e);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            Files.deleteIfExists(resolve(objectKey));
        } catch (IOException e) {
            throw new IllegalStateException("本地文件删除失败", e);
        }
    }

    @Override
    public void checkHealth() {
        try {
            Files.createDirectories(root);
            if (!Files.isWritable(root)) {
                throw new IllegalStateException("本地文件目录不可写");
            }
        } catch (IOException e) {
            throw new IllegalStateException("本地文件目录不可用", e);
        }
    }

    private Path resolve(String objectKey) {
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("非法对象键");
        }
        return target;
    }
}
