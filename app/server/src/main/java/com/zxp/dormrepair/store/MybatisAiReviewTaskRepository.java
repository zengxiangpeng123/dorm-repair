package com.zxp.dormrepair.store;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zxp.dormrepair.common.BizException;
import com.zxp.dormrepair.common.TimeUtil;
import com.zxp.dormrepair.domain.AiReviewTask;
import com.zxp.dormrepair.domain.AiReviewTaskStatus;
import com.zxp.dormrepair.store.entity.AiReviewTaskRow;
import com.zxp.dormrepair.store.mapper.AiReviewTaskMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/** MySQL AI 复核任务仓储。 */
@Repository
@ConditionalOnProperty(name = "app.store", havingValue = "mysql")
public class MybatisAiReviewTaskRepository implements AiReviewTaskRepository {
    private final AiReviewTaskMapper mapper;

    public MybatisAiReviewTaskRepository(AiReviewTaskMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public AiReviewTask create(String orderNo, int orderVersion, String inputHash, String model, String provider) {
        AiReviewTaskRow row = new AiReviewTaskRow();
        row.setId(UUID.randomUUID().toString());
        row.setOrderNo(orderNo);
        row.setOrderVersion(orderVersion);
        row.setInputHash(inputHash);
        row.setModel(model);
        row.setProvider(provider);
        row.setStatus(AiReviewTaskStatus.PENDING.name());
        row.setAttempts(0);
        row.setRequestedAt(TimeUtil.now());
        row.setApplied(false);
        mapper.insert(row);
        return toTask(row);
    }

    @Override
    public Optional<AiReviewTask> find(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toTask);
    }

    @Override
    public Optional<AiReviewTask> latest(String orderNo) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<AiReviewTaskRow>query()
                .eq("order_no", orderNo).orderByDesc("requested_at").last("LIMIT 1"))).map(this::toTask);
    }

    @Override
    public List<AiReviewTask> recent(int limit) {
        return mapper.selectList(Wrappers.<AiReviewTaskRow>query()
                        .orderByDesc("requested_at").last("LIMIT " + Math.max(1, Math.min(limit, 100))))
                .stream().map(this::toTask).toList();
    }

    @Override
    public void markRunning(String id, int attempts) {
        if (mapper.markRunning(id, attempts) != 1) {
            throw BizException.conflict("AI_REVIEW_NOT_PENDING", "AI 复核任务已被其他实例处理");
        }
    }

    @Override
    public void markResult(String id, AiReviewTaskStatus status, int attempts, String errorCode, String errorMessage,
                           String category, String level, Double confidence, String basis, String source,
                           long latencyMs) {
        if (mapper.markResult(id, status.name(), attempts, errorCode, errorMessage, category, level,
                confidence, basis, source, latencyMs, TimeUtil.now()) != 1) {
            throw BizException.conflict("AI_REVIEW_NOT_RUNNING", "AI 复核任务状态已变化");
        }
    }

    @Override
    public void markApplied(String id, String appliedAt) {
        if (mapper.markApplied(id, appliedAt) != 1) {
            throw BizException.conflict("AI_REVIEW_ALREADY_APPLIED", "AI 复核结果已应用");
        }
    }

    @Override
    public void markSuperseded(String id, String reason) {
        if (mapper.markSuperseded(id, reason, TimeUtil.now()) != 1) {
            throw BizException.conflict("AI_REVIEW_TERMINAL", "AI 复核任务已是终态");
        }
    }

    @Override
    public void clear() {
        mapper.wipe();
    }

    private void update(String id, java.util.function.Consumer<AiReviewTaskRow> mutator) {
        AiReviewTaskRow row = mapper.selectById(id);
        if (row == null) throw BizException.notFound("AI 复核任务不存在：" + id);
        mutator.accept(row);
        mapper.updateById(row);
    }

    private AiReviewTask toTask(AiReviewTaskRow r) {
        return new AiReviewTask(r.getId(), r.getOrderNo(), r.getOrderVersion() == null ? 0 : r.getOrderVersion(),
                r.getInputHash(), r.getModel(), r.getProvider(), AiReviewTaskStatus.of(r.getStatus()),
                r.getAttempts() == null ? 0 : r.getAttempts(), r.getRequestedAt(), r.getCompletedAt(),
                r.getErrorCode(), r.getErrorMessage(), r.getCategory(), r.getLevel(), r.getConfidence(),
                r.getBasis(), r.getSource(), r.getLatencyMs() == null ? 0L : r.getLatencyMs(),
                Boolean.TRUE.equals(r.getApplied()), r.getAppliedAt());
    }
}
