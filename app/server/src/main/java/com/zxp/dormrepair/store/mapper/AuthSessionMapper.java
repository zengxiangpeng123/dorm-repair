package com.zxp.dormrepair.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zxp.dormrepair.store.entity.AuthSessionRow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AuthSessionMapper extends BaseMapper<AuthSessionRow> {

    @Update("UPDATE t_auth_session SET revoked_at_ms=#{nowMs} WHERE token_hash=#{tokenHash} AND revoked_at_ms IS NULL")
    int revoke(@Param("tokenHash") String tokenHash, @Param("nowMs") long nowMs);

    @Update("UPDATE t_auth_session SET revoked_at_ms=#{nowMs} " +
            "WHERE account=#{account} AND revoked_at_ms IS NULL AND expires_at_ms > #{nowMs}")
    int revokeAccount(@Param("account") String account, @Param("nowMs") long nowMs);

    @Delete("DELETE FROM t_auth_session WHERE expires_at_ms <= #{nowMs} OR revoked_at_ms IS NOT NULL")
    int deleteExpired(@Param("nowMs") long nowMs);
}
