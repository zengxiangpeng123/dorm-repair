package com.zxp.dormrepair.file;

import java.io.InputStream;
import java.util.Optional;

/** 文件对象存储。业务层只依赖这个接口，不直接访问本地磁盘或云 SDK。 */
public interface FileStorage {

    void put(String objectKey, InputStream input, long contentLength, String contentType);

    /** 仅在对象不存在时写入，返回是否由本次写入。实现必须使用原子条件写。 */
    boolean putIfAbsent(String objectKey, InputStream input, long contentLength, String contentType);

    Optional<StoredObject> open(String objectKey);

    /** 删除对象；对象本来就不存在也应视为成功，保证清理可重入。 */
    void delete(String objectKey);

    /** 供就绪探针确认存储后端当前可访问；失败必须抛异常。 */
    default void checkHealth() {
    }

    default String type() {
        String name = getClass().getSimpleName();
        return name == null || name.isBlank() ? "unknown" : name;
    }

    record StoredObject(InputStream stream, long contentLength, String contentType) {
    }
}
