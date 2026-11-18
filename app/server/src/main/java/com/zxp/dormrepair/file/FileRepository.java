package com.zxp.dormrepair.file;

import java.util.List;
import java.util.Optional;

public interface FileRepository {
    FileRecord create(String objectKey, String originalName, String mime, long byteSize,
                      String sha256, String uploadedBy, String purpose);
    Optional<FileRecord> find(String id);
    List<FileRecord> findByIds(List<String> ids);
    void bind(String id, String orderNo, String kind);
}
