package com.zxp.dormrepair.store.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** t_config 两列：k / v。够用就好，别把配置做成一张宽表。 */
@Mapper
public interface ConfigMapper {

    @Select("SELECT k, v FROM t_config")
    List<Map<String, Object>> all();

    @Insert("""
            INSERT INTO t_config (k, v, updated_at) VALUES (#{k}, #{v}, #{at})
            ON DUPLICATE KEY UPDATE v = #{v}, updated_at = #{at}
            """)
    int upsert(String k, String v, String at);
}
