package com.zxp.dormrepair.store;

import com.zxp.dormrepair.domain.IdempotencyRecord;
import java.util.Optional;

/** 关键写请求的幂等记录；唯一键由 actor + method + path + key 组成。 */
public interface IdempotencyRepository {
    Optional<IdempotencyRecord> find(String actorType, String actorId, String method, String path, String key);
    /** 原子占位；返回 false 表示已被其他请求占用。 */
    boolean claim(IdempotencyRecord record);
    /** 把处理中的占位写成最终成功响应。 */
    void complete(String id, int responseStatus, String responseBody, String completedAt);
    /** 失败时释放占位，允许同一 key 修正后重试。 */
    void release(String id);
    void clear();
}
