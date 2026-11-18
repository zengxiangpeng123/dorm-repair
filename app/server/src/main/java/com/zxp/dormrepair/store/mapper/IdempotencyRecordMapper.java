package com.zxp.dormrepair.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zxp.dormrepair.store.entity.IdempotencyRecordRow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface IdempotencyRecordMapper extends BaseMapper<IdempotencyRecordRow> {
    @Insert("""
            INSERT IGNORE INTO t_idempotency_record
              (id, actor_type, actor_id, method, path, idempotency_key, request_hash,
               status, created_at)
            VALUES
              (#{row.id}, #{row.actorType}, #{row.actorId}, #{row.method}, #{row.path}, #{row.idempotencyKey},
               #{row.requestHash}, 'PROCESSING',
               #{row.createdAt,typeHandler=com.zxp.dormrepair.store.DateTimeStringTypeHandler})
            """)
    int claim(@Param("row") IdempotencyRecordRow row, @Param("staleBefore") String staleBefore);

    @Update("""
            UPDATE t_idempotency_record
               SET status = 'PROCESSING', request_hash = #{row.requestHash},
                   response_status = NULL, response_body = NULL,
                   created_at = #{row.createdAt,typeHandler=com.zxp.dormrepair.store.DateTimeStringTypeHandler},
                   completed_at = NULL
             WHERE actor_type = #{row.actorType} AND actor_id = #{row.actorId}
               AND method = #{row.method} AND path = #{row.path}
               AND idempotency_key = #{row.idempotencyKey}
               AND status = 'PROCESSING'
               AND created_at < #{staleBefore,typeHandler=com.zxp.dormrepair.store.DateTimeStringTypeHandler}
            """)
    int takeOver(@Param("row") IdempotencyRecordRow row, @Param("staleBefore") String staleBefore);

    @Update("""
            UPDATE t_idempotency_record
               SET status = 'COMPLETED', response_status = #{responseStatus},
                   response_body = #{responseBody},
                   completed_at = #{completedAt,typeHandler=com.zxp.dormrepair.store.DateTimeStringTypeHandler}
             WHERE id = #{id} AND status = 'PROCESSING'
            """)
    int complete(@Param("id") String id, @Param("responseStatus") int responseStatus,
                 @Param("responseBody") String responseBody, @Param("completedAt") String completedAt);

    @Delete("DELETE FROM t_idempotency_record WHERE id=#{id} AND status='PROCESSING'")
    int release(@Param("id") String id);

    @Delete("DELETE FROM t_idempotency_record")
    int wipe();
}
