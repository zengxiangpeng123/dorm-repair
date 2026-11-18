package com.zxp.dormrepair.store;

import com.zxp.dormrepair.common.BizException;
import com.zxp.dormrepair.common.TimeUtil;
import com.zxp.dormrepair.domain.AiReviewTask;
import com.zxp.dormrepair.domain.AiReviewTaskStatus;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/** 内存 AI 复核任务仓储，测试用。 */
@Repository
@ConditionalOnProperty(name = "app.store", havingValue = "memory", matchIfMissing = true)
public class InMemoryAiReviewTaskRepository implements AiReviewTaskRepository {
    private final ConcurrentHashMap<String, AiReviewTask> tasks = new ConcurrentHashMap<>();

    @Override
    public AiReviewTask create(String orderNo, int orderVersion, String inputHash, String model, String provider) {
        AiReviewTask t = new AiReviewTask(UUID.randomUUID().toString(), orderNo, orderVersion, inputHash,
                model, provider, AiReviewTaskStatus.PENDING, 0, TimeUtil.now(), null, null, null,
                null, null, null, null, null, 0L, false, null);
        tasks.put(t.id(), t);
        return t;
    }

    @Override
    public Optional<AiReviewTask> find(String id) {
        return Optional.ofNullable(tasks.get(id));
    }

    @Override
    public Optional<AiReviewTask> latest(String orderNo) {
        return tasks.values().stream().filter(t -> orderNo.equals(t.orderNo()))
                .max(Comparator.comparing(AiReviewTask::requestedAt));
    }

    @Override
    public List<AiReviewTask> recent(int limit) {
        return tasks.values().stream().sorted(Comparator.comparing(AiReviewTask::requestedAt).reversed())
                .limit(Math.max(1, Math.min(limit, 100))).toList();
    }

    @Override
    public void markRunning(String id, int attempts) {
        update(id, t -> new AiReviewTask(t.id(), t.orderNo(), t.orderVersion(), t.inputHash(), t.model(),
                t.provider(), AiReviewTaskStatus.RUNNING, attempts, t.requestedAt(), t.completedAt(),
                t.errorCode(), t.errorMessage(), t.category(), t.level(), t.confidence(), t.basis(),
                t.source(), t.latencyMs(), t.applied(), t.appliedAt()));
    }

    @Override
    public void markResult(String id, AiReviewTaskStatus status, int attempts, String errorCode, String errorMessage,
                           String category, String level, Double confidence, String basis, String source,
                           long latencyMs) {
        update(id, t -> new AiReviewTask(t.id(), t.orderNo(), t.orderVersion(), t.inputHash(), t.model(),
                t.provider(), status, attempts, t.requestedAt(), TimeUtil.now(), errorCode, errorMessage,
                category, level, confidence, basis, source, latencyMs, t.applied(), t.appliedAt()));
    }

    @Override
    public void markApplied(String id, String appliedAt) {
        update(id, t -> new AiReviewTask(t.id(), t.orderNo(), t.orderVersion(), t.inputHash(), t.model(),
                t.provider(), t.status(), t.attempts(), t.requestedAt(), t.completedAt(), t.errorCode(),
                t.errorMessage(), t.category(), t.level(), t.confidence(), t.basis(), t.source(),
                t.latencyMs(), true, appliedAt));
    }

    @Override
    public void markSuperseded(String id, String reason) {
        update(id, t -> new AiReviewTask(t.id(), t.orderNo(), t.orderVersion(), t.inputHash(), t.model(),
                t.provider(), AiReviewTaskStatus.SUPERSEDED, t.attempts(), t.requestedAt(), TimeUtil.now(),
                "STALE_ORDER", reason, t.category(), t.level(), t.confidence(), t.basis(), t.source(),
                t.latencyMs(), t.applied(), t.appliedAt()));
    }

    @Override
    public int failStale(String staleBefore, String errorCode, String errorMessage) {
        long cutoff = TimeUtil.epochMs(staleBefore);
        int[] changed = {0};
        tasks.replaceAll((id, t) -> {
            Long requested = TimeUtil.epochMs(t.requestedAt());
            if ((t.status() == AiReviewTaskStatus.PENDING || t.status() == AiReviewTaskStatus.RUNNING)
                    && requested != null && requested < cutoff) {
                changed[0]++;
                return new AiReviewTask(t.id(), t.orderNo(), t.orderVersion(), t.inputHash(), t.model(),
                        t.provider(), AiReviewTaskStatus.FAILED, t.attempts(), t.requestedAt(), TimeUtil.now(),
                        errorCode, errorMessage, t.category(), t.level(), t.confidence(), t.basis(),
                        t.source(), t.latencyMs(), t.applied(), t.appliedAt());
            }
            return t;
        });
        return changed[0];
    }

    @Override
    public void clear() {
        tasks.clear();
    }

    private void update(String id, java.util.function.Function<AiReviewTask, AiReviewTask> fn) {
        tasks.compute(id, (k, old) -> {
            if (old == null) throw BizException.notFound("AI 复核任务不存在：" + id);
            return fn.apply(old);
        });
    }
}
