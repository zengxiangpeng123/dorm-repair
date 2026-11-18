package com.zxp.dormrepair.store;

import com.zxp.dormrepair.domain.ReworkReview;
import java.util.ArrayList;
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
public class InMemoryReworkReviewRepository implements ReworkReviewRepository {

    private final Map<Long, ReworkReview> rows = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong();

    @Override
    public ReworkReview createRequest(String orderNo, String requestedBy, String reason, String at) {
        int attempt = rows.values().stream()
                .filter(r -> r.orderNo().equals(orderNo))
                .mapToInt(ReworkReview::attemptNo).max().orElse(0) + 1;
        long id = seq.incrementAndGet();
        ReworkReview r = new ReworkReview(id, orderNo, attempt, null, "PENDING",
                requestedBy, reason, at, null, null, null);
        rows.put(id, r);
        return r;
    }

    @Override
    public Optional<ReworkReview> findPending(String orderNo) {
        return rows.values().stream()
                .filter(r -> r.orderNo().equals(orderNo) && "PENDING".equals(r.status()))
                .max(Comparator.comparingLong(ReworkReview::id));
    }

    @Override
    public Optional<ReworkReview> findLatest(String orderNo) {
        return rows.values().stream()
                .filter(r -> r.orderNo().equals(orderNo))
                .max(Comparator.comparingLong(ReworkReview::id));
    }

    @Override
    public List<ReworkReview> pending() {
        return rows.values().stream()
                .filter(r -> "PENDING".equals(r.status()))
                .sorted(Comparator.comparing(ReworkReview::requestedAt))
                .toList();
    }

    @Override
    public ReworkReview decide(ReworkReview review, String status, Integer reworkIndex,
                               String decidedBy, String reason, String at) {
        ReworkReview changed = new ReworkReview(review.id(), review.orderNo(), review.attemptNo(),
                reworkIndex, status, review.requestedBy(), review.requestReason(), review.requestedAt(),
                decidedBy, reason, at);
        rows.put(review.id(), changed);
        return changed;
    }

    @Override
    public boolean hasPending(String orderNo) {
        return findPending(orderNo).isPresent();
    }

    @Override
    public void clear() {
        rows.clear();
        seq.set(0);
    }
}