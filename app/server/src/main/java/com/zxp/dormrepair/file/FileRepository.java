package com.zxp.dormrepair.file;

import java.util.List;
import java.util.Optional;

public interface FileRepository {
    FileRecord create(String objectKey, String originalName, String mime, long byteSize,
                      String sha256, String uploadedBy, String purpose);
    Optional<FileRecord> find(String id);
    List<FileRecord> findByIds(List<String> ids);
    void bind(String id, String orderNo, String kind);

    /** 只绑定尚未归属工单的文件；任一张已被占用时整批失败。 */
    void bindAll(List<String> ids, String orderNo, String kind);

    /** 返回创建时间早于 cutoff、且尚未绑定工单的候选文件。 */
    List<FileRecord> findUnboundCreatedBefore(String cutoff, int limit);

    /** 原子地占用一条孤儿记录，避免多个实例重复清理同一对象。 */
    boolean claimForCleanup(String id, String claimedAt);

    /** 回收进程崩溃后遗留的过期清理租约。 */
    int releaseStaleClaims(String staleBefore);

    /** 对象已删除后移除元数据行。 */
    void delete(String id);

    /** 清理失败时释放占用，下一轮可重试。 */
    void releaseClaim(String id);
}
