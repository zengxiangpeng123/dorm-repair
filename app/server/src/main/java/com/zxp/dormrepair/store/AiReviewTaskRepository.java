package com.zxp.dormrepair.store;

import com.zxp.dormrepair.domain.AiReviewTask;
import com.zxp.dormrepair.domain.AiReviewTaskStatus;
import java.util.List;
import java.util.Optional;

/** AI 复核任务仓储；任务事实只追加，结果应用另行记录。 */
public interface AiReviewTaskRepository {
    AiReviewTask create(String orderNo, int orderVersion, String inputHash, String model, String provider);
    Optional<AiReviewTask> find(String id);
    Optional<AiReviewTask> latest(String orderNo);
    List<AiReviewTask> recent(int limit);
    void markRunning(String id, int attempts);
    void markResult(String id, AiReviewTaskStatus status, int attempts, String errorCode, String errorMessage,
                    String category, String level, Double confidence, String basis, String source,
                    long latencyMs);
    void markApplied(String id, String appliedAt);
    void markSuperseded(String id, String reason);
    /** 把进程中断后超过租约的 PENDING/RUNNING 任务标记为失败。 */
    int failStale(String staleBefore, String errorCode, String errorMessage);
    void clear();
}
