package com.zxp.dormrepair.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zxp.dormrepair.store.entity.AiReviewTaskRow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AiReviewTaskMapper extends BaseMapper<AiReviewTaskRow> {
    @Delete("DELETE FROM t_ai_review_task")
    int wipe();

    @Update("UPDATE t_ai_review_task SET status='RUNNING', attempts=#{attempts} " +
            "WHERE id=#{id} AND status='PENDING'")
    int markRunning(@Param("id") String id, @Param("attempts") int attempts);

    @Update("UPDATE t_ai_review_task SET status=#{status}, attempts=#{attempts}, " +
            "error_code=#{errorCode}, error_message=#{errorMessage}, category=#{category}, " +
            "level=#{level}, confidence=#{confidence}, basis=#{basis}, source=#{source}, " +
            "latency_ms=#{latencyMs}, completed_at=#{completedAt} " +
            "WHERE id=#{id} AND status='RUNNING'")
    int markResult(@Param("id") String id, @Param("status") String status,
                   @Param("attempts") int attempts, @Param("errorCode") String errorCode,
                   @Param("errorMessage") String errorMessage, @Param("category") String category,
                   @Param("level") String level, @Param("confidence") Double confidence,
                   @Param("basis") String basis, @Param("source") String source,
                   @Param("latencyMs") long latencyMs, @Param("completedAt") String completedAt);

    @Update("UPDATE t_ai_review_task SET applied=1, applied_at=#{appliedAt} " +
            "WHERE id=#{id} AND applied=0")
    int markApplied(@Param("id") String id, @Param("appliedAt") String appliedAt);

    @Update("UPDATE t_ai_review_task SET status='SUPERSEDED', error_code='STALE_ORDER', " +
            "error_message=#{reason}, completed_at=#{completedAt} " +
            "WHERE id=#{id} AND status IN ('PENDING','RUNNING','SUCCEEDED','FALLBACK')")
    int markSuperseded(@Param("id") String id, @Param("reason") String reason,
                       @Param("completedAt") String completedAt);

    @Update("UPDATE t_ai_review_task SET status='FAILED', error_code=#{errorCode}, " +
            "error_message=#{errorMessage}, completed_at=#{completedAt} " +
            "WHERE status IN ('PENDING','RUNNING') AND requested_at < #{staleBefore}")
    int failStale(@Param("staleBefore") String staleBefore,
                  @Param("errorCode") String errorCode,
                  @Param("errorMessage") String errorMessage,
                  @Param("completedAt") String completedAt);
}
