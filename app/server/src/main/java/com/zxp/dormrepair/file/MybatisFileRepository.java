package com.zxp.dormrepair.file;

import com.zxp.dormrepair.common.BizException;
import com.zxp.dormrepair.common.TimeUtil;
import com.zxp.dormrepair.store.mapper.FileRecordMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/** 文件元数据仓库。对象存储只保存字节，权限与归属以这里和工单为准。 */
@Repository
@ConditionalOnProperty(name = "app.file.metadata", havingValue = "mysql", matchIfMissing = true)
public class MybatisFileRepository implements FileRepository {

    private final FileRecordMapper mapper;

    public MybatisFileRepository(FileRecordMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public FileRecord create(String objectKey, String originalName, String mime, long byteSize,
                             String sha256, String uploadedBy, String purpose) {
        FileRecordRow row = new FileRecordRow();
        row.setId(UUID.randomUUID().toString());
        row.setObjectKey(objectKey);
        row.setOriginalName(originalName);
        row.setMime(mime);
        row.setByteSize(byteSize);
        row.setSha256(sha256);
        row.setUploadedBy(uploadedBy);
        row.setPurpose(purpose);
        row.setVisibility("PRIVATE");
        row.setCreatedAt(TimeUtil.now());
        mapper.insert(row);
        return toRecord(row);
    }

    @Override
    public Optional<FileRecord> find(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toRecord);
    }

    @Override
    public List<FileRecord> findByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return mapper.selectBatchIds(ids).stream().map(this::toRecord).toList();
    }

    @Override
    public void bind(String id, String orderNo, String kind) {
        FileRecordRow row = mapper.selectById(id);
        if (row == null) throw BizException.notFound("文件不存在：" + id);
        if (row.getOrderNo() != null && row.getOrderNo().equals(orderNo) && row.getKind() != null) {
            return;
        }
        if (row.getOrderNo() != null) {
            throw BizException.precondition("文件已经绑定到其他工单", "FILE_ALREADY_BOUND");
        }
        row.setOrderNo(orderNo);
        row.setKind(kind);
        mapper.updateById(row);
    }

    private FileRecord toRecord(FileRecordRow r) {
        return new FileRecord(r.getId(), r.getObjectKey(), r.getOriginalName(), r.getMime(),
                r.getByteSize() == null ? 0 : r.getByteSize(), r.getSha256(), r.getUploadedBy(),
                r.getPurpose(), r.getOrderNo(), r.getKind(), r.getCreatedAt());
    }
}
