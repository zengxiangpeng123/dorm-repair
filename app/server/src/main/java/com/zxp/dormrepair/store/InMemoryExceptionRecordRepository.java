package com.zxp.dormrepair.store;

import com.zxp.dormrepair.domain.ExceptionRecord;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.store", havingValue = "memory", matchIfMissing = true)
public class InMemoryExceptionRecordRepository implements ExceptionRecordRepository {

    private final Map<Long, ExceptionRecord> rows = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong();

    @Override
    public Optional<ExceptionRecord> findOpen(String kind, String orderNo) {
        return rows.values().stream()
                .filter(r -> kind.equals(r.kind()) && orderNo.equals(r.orderNo()) && "OPEN".equals(r.status()))
                .max(Comparator.comparingLong(ExceptionRecord::id));
    }

    @Override
    public ExceptionRecord createRepeatRework(String orderNo, String detail, String at) {
        long id = seq.incrementAndGet();
        ExceptionRecord row = new ExceptionRecord(id, "REPEAT_REWORK", orderNo, detail,
                "OPEN", null, at, null);
        rows.put(id, row);
        return row;
    }

    @Override
    public List<ExceptionRecord> openByKind(String kind) {
        return rows.values().stream()
                .filter(r -> kind.equals(r.kind()) && "OPEN".equals(r.status()))
                .sorted(Comparator.comparing(ExceptionRecord::createdAt))
                .toList();
    }

    @Override
    public void clear() {
        rows.clear();
        seq.set(0);
    }
}
