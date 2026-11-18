package com.zxp.dormrepair.store;

import com.zxp.dormrepair.common.TimeUtil;
import com.zxp.dormrepair.domain.IdempotencyRecord;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.store", havingValue = "memory", matchIfMissing = true)
public class InMemoryIdempotencyRepository implements IdempotencyRepository {
    private final ConcurrentHashMap<String, IdempotencyRecord> rows = new ConcurrentHashMap<>();

    @Override
    public Optional<IdempotencyRecord> find(String actorType, String actorId, String method, String path, String key) {
        return Optional.ofNullable(rows.get(compound(actorType, actorId, method, path, key)));
    }

    @Override
    public boolean claim(IdempotencyRecord record, String staleBefore) {
        String key = compound(record.actorType(), record.actorId(), record.method(), record.path(),
                record.idempotencyKey());
        var replaced = new java.util.concurrent.atomic.AtomicBoolean(false);
        rows.compute(key, (k, current) -> {
            if (current == null) {
                replaced.set(true);
                return record;
            }
            Long created = TimeUtil.epochMs(current.createdAt());
            Long cutoff = TimeUtil.epochMs(staleBefore);
            if ("PROCESSING".equals(current.status()) && created != null && cutoff != null
                    && created < cutoff) {
                replaced.set(true);
                return record;
            }
            return current;
        });
        return replaced.get();
    }

    @Override
    public void complete(String id, int responseStatus, String responseBody, String completedAt) {
        rows.replaceAll((k, r) -> r.id().equals(id) && "PROCESSING".equals(r.status())
                ? new IdempotencyRecord(r.id(), r.actorType(), r.actorId(), r.method(), r.path(),
                        r.idempotencyKey(), r.requestHash(), "COMPLETED", responseStatus,
                        responseBody, r.createdAt(), completedAt)
                : r);
    }

    @Override
    public void release(String id) {
        rows.entrySet().removeIf(e -> e.getValue().id().equals(id)
                && "PROCESSING".equals(e.getValue().status()));
    }

    @Override
    public void clear() {
        rows.clear();
    }

    private static String compound(String actorType, String actorId, String method, String path, String key) {
        return actorType + "\n" + actorId + "\n" + method + "\n" + path + "\n" + key;
    }
}
