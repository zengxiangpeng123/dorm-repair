package com.zxp.dormrepair.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zxp.dormrepair.store.entity.LoginAttemptRow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface LoginAttemptMapper extends BaseMapper<LoginAttemptRow> {
    @Select("SELECT failures, blocked_until_ms, updated_at_ms FROM t_login_attempt WHERE attempt_key=#{key}")
    LoginAttemptRow find(@Param("key") String key);

    @Update("UPDATE t_login_attempt SET failures=failures+1, blocked_until_ms=CASE WHEN failures+1 >= #{threshold} THEN #{blockedUntil} ELSE 0 END, updated_at_ms=#{now} WHERE attempt_key=#{key}")
    int increment(@Param("key") String key, @Param("threshold") int threshold,
                  @Param("blockedUntil") long blockedUntil, @Param("now") long now);

    @Update("DELETE FROM t_login_attempt WHERE attempt_key=#{key}")
    int clear(@Param("key") String key);

    @Delete("DELETE FROM t_login_attempt WHERE blocked_until_ms > 0 AND blocked_until_ms <= #{now} OR updated_at_ms < #{staleBefore}")
    int deleteExpired(@Param("now") long now, @Param("staleBefore") long staleBefore);
}
