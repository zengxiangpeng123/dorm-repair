package com.zxp.dormrepair.store;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zxp.dormrepair.domain.IdempotencyRecord;
import com.zxp.dormrepair.store.entity.IdempotencyRecordRow;
import com.zxp.dormrepair.store.mapper.IdempotencyRecordMapper;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.store", havingValue = "mysql")
public class MybatisIdempotencyRepository implements IdempotencyRepository {
    private final IdempotencyRecordMapper mapper;

    public MybatisIdempotencyRepository(IdempotencyRecordMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<IdempotencyRecord> find(String actorType, String actorId, String method, String path, String key) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<IdempotencyRecordRow>query()
                .eq("actor_type", actorType).eq("actor_id", actorId).eq("method", method)
                .eq("path", path).eq("idempotency_key", key).last("LIMIT 1"))).map(this::toDomain);
    }

    @Override
    public boolean claim(IdempotencyRecord record, String staleBefore) {
        IdempotencyRecordRow row = new IdempotencyRecordRow();
        row.setId(record.id());
        row.setActorType(record.actorType());
        row.setActorId(record.actorId());
        row.setMethod(record.method());
        row.setPath(record.path());
        row.setIdempotencyKey(record.idempotencyKey());
        row.setRequestHash(record.requestHash());
        row.setStatus("PROCESSING");
        row.setCreatedAt(record.createdAt());
        if (mapper.claim(row, staleBefore) == 1) return true;
        return mapper.takeOver(row, staleBefore) == 1;
    }

    @Override
    public void complete(String id, int responseStatus, String responseBody, String completedAt) {
        mapper.complete(id, responseStatus, responseBody, completedAt);
    }

    @Override
    public void release(String id) {
        mapper.release(id);
    }

    @Override
    public void clear() {
        mapper.wipe();
    }

    private IdempotencyRecord toDomain(IdempotencyRecordRow r) {
        return new IdempotencyRecord(r.getId(), r.getActorType(), r.getActorId(), r.getMethod(),
                r.getPath(), r.getIdempotencyKey(), r.getRequestHash(), r.getStatus(),
                r.getResponseStatus(), r.getResponseBody(), r.getCreatedAt(), r.getCompletedAt());
    }
}
