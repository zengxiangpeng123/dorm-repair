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
              (#{id}, #{actorType}, #{actorId}, #{method}, #{path}, #{idempotencyKey},
               #{requestHash}, 'PROCESSING',
               #{createdAt,typeHandler=com.zxp.dormrepair.store.DateTimeStringTypeHandler})
            """)
    int claim(IdempotencyRecordRow row);

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
