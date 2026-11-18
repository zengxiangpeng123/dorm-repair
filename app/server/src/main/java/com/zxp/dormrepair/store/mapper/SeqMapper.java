package com.zxp.dormrepair.store.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 单号序号：按天递增。
 *
 * <p>先 upsert 再读，读到的值减一就是刚领走的号 —— MySQL 的 upsert 是原子的，两个请求不会拿到同一个号。
 */
@Mapper
public interface SeqMapper {

    @Insert("""
            INSERT INTO t_seq (day, name, next_val) VALUES (#{day}, #{name}, 2)
            ON DUPLICATE KEY UPDATE next_val = next_val + 1
            """)
    int bump(@Param("day") String day, @Param("name") String name);

    @Select("SELECT next_val FROM t_seq WHERE day = #{day} AND name = #{name}")
    Integer current(@Param("day") String day, @Param("name") String name);
}
