package com.zxp.dormrepair.store.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 单号序号：按天递增。递增与锁定读取必须在同一事务内完成。
 */
@Mapper
public interface SeqMapper {

    @Select("""
            SELECT next_val
            FROM t_seq
            WHERE day = #{day} AND name = #{name}
            FOR UPDATE
            """)
    Integer lockCurrent(@Param("day") String day, @Param("name") String name);

    @Insert("""
            INSERT INTO t_seq (day, name, next_val) VALUES (#{day}, #{name}, 2)
            ON DUPLICATE KEY UPDATE next_val = next_val + 1
            """)
    int bump(@Param("day") String day, @Param("name") String name);
}
