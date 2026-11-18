package com.zxp.dormrepair.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zxp.dormrepair.store.entity.UserRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 人员表。只需要按 id 取、列全表、改口令三件事。
 */
@Mapper
public interface UserMapper extends BaseMapper<UserRow> {

    @Update("UPDATE t_user SET password_hash = #{hash} WHERE id = #{id}")
    int setPasswordHash(@Param("id") String id, @Param("hash") String hash);
}
