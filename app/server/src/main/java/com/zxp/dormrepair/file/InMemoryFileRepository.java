package com.zxp.dormrepair.file;

import com.zxp.dormrepair.common.BizException;
import com.zxp.dormrepair.common.TimeUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/** 测试/内存模式的文件元数据仓库。 */
@Repository
@ConditionalOnProperty(name = "app.file.metadata", havingValue = "memory")
public class InMemoryFileRepository implements FileRepository {

    private final ConcurrentHashMap<String, FileRecord> files = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> unboundByDigest = new ConcurrentHashMap<>();

    @Override
    public FileRecord create(String objectKey, String originalName, String mime, long byteSize,
                             String sha256, String uploadedBy, String purpose) {
        return createUnbound(objectKey, originalName, mime, byteSize, sha256, uploadedBy, purpose);
    }

    private FileRecord createUnbound(String objectKey, String originalName, String mime, long byteSize,
                                     String sha256, String uploadedBy, String purpose) {
        String id = UUID.randomUUID().toString();
        FileRecord f = new FileRecord(id, objectKey, originalName, mime, byteSize, sha256,
                uploadedBy, purpose, null, null, TimeUtil.now());
        files.put(id, f);
        return f;
    }

    @Override
    public Optional<FileRecord> find(String id) {
        return Optional.ofNullable(files.get(id));
    }

    @Override
    public List<FileRecord> findByIds(List<String> ids) {
        List<FileRecord> out = new ArrayList<>();
        if (ids != null) {
            ids.forEach(id -> {
                FileRecord f = files.get(id);
                if (f != null) out.add(f);
            });
        }
        return out;
    }

    @Override
    public FileRecord findOrCreateUnbound(String objectKey, String originalName, String mime,
                                          long byteSize, String sha256, String uploadedBy, String purpose) {
        String digestKey = digestKey(uploadedBy, purpose, sha256);
        String id = unboundByDigest.compute(digestKey, (key, existingId) -> {
            if (existingId != null && files.containsKey(existingId)) return existingId;
            FileRecord created = createUnbound(objectKey, originalName, mime, byteSize, sha256, uploadedBy, purpose);
            return created.id();
        });
        return files.get(id);
    }
    @Override
    public void bind(String id, String orderNo, String kind) {
        FileRecord bound = files.compute(id, (key, f) -> {
            if (f == null) throw BizException.notFound("文件不存在：" + id);
            if (f.orderNo() != null && f.orderNo().equals(orderNo) && f.kind() != null) return f;
            if (f.orderNo() != null) throw BizException.precondition("文件已经绑定到其他工单", "FILE_ALREADY_BOUND");
            return new FileRecord(f.id(), f.objectKey(), f.originalName(), f.mime(), f.byteSize(),
                    f.sha256(), f.uploadedBy(), f.purpose(), orderNo, kind, f.createdAt());
        });
        unboundByDigest.remove(digestKey(bound.uploadedBy(), bound.purpose(), bound.sha256()), id);
    }

    private static String digestKey(String uploadedBy, String purpose, String sha256) {
        return uploadedBy + "\n" + purpose + "\n" + sha256;
    }

    @Override
    public void bindAll(List<String> ids, String orderNo, String kind) {
        if (ids == null || ids.isEmpty()) return;
        for (String id : ids) bind(id, orderNo, kind);
    }

    @Override
    public List<FileRecord> findUnboundCreatedBefore(String cutoff, int limit) {
        long cutoffMs = TimeUtil.epochMs(cutoff);
        return files.values().stream()
                .filter(f -> f.orderNo() == null)
                .filter(f -> TimeUtil.epochMs(f.createdAt()) < cutoffMs)
                .limit(limit)
                .toList();
    }

    private final java.util.Map<String, String> cleanupClaims = new ConcurrentHashMap<>();

    @Override
    public boolean claimForCleanup(String id, String claimedAt) {
        return cleanupClaims.putIfAbsent(id, claimedAt) == null;
    }

    @Override
    public int releaseStaleClaims(String staleBefore) {
        long cutoff = TimeUtil.epochMs(staleBefore);
        int released = 0;
        for (var entry : cleanupClaims.entrySet()) {
            Long claimedAt = TimeUtil.epochMs(entry.getValue());
            if (claimedAt != null && claimedAt < cutoff
                    && cleanupClaims.remove(entry.getKey(), entry.getValue())) {
                released++;
            }
        }
        return released;
    }

    @Override
    public void delete(String id) {
        FileRecord removed = files.remove(id);
        if (removed != null) unboundByDigest.remove(digestKey(removed.uploadedBy(), removed.purpose(), removed.sha256()), id);
        cleanupClaims.remove(id);
    }

    @Override
    public void deleteIfUnbound(String id) {
        files.computeIfPresent(id, (key, f) -> {
            if (f.orderNo() != null) return f;
            unboundByDigest.remove(digestKey(f.uploadedBy(), f.purpose(), f.sha256()), id);
            return null;
        });
    }

    @Override
    public void releaseClaim(String id) {
        cleanupClaims.remove(id);
    }
}
