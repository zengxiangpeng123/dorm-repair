package com.zxp.dormrepair.file;

import java.io.InputStream;
import java.util.Optional;

/** 文件对象存储。业务层只依赖这个接口，不直接访问本地磁盘或云 SDK。 */
public interface FileStorage {

    void put(String objectKey, InputStream input, long contentLength, String contentType);

    Optional<StoredObject> open(String objectKey);

    void delete(String objectKey);

    record StoredObject(InputStream stream, long contentLength, String contentType) {
    }
}
