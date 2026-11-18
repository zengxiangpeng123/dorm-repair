package com.zxp.dormrepair.file;

public record FileRecord(
        String id,
        String objectKey,
        String originalName,
        String mime,
        long byteSize,
        String sha256,
        String uploadedBy,
        String purpose,
        String orderNo,
        String kind,
        String createdAt) {
}
