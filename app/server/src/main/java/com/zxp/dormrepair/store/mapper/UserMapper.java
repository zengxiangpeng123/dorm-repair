package com.zxp.dormrepair.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zxp.dormrepair.store.entity.UserRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 人员兼容视图用于读取；新增与改口令按角色写入独立表。
 */
@Mapper
public interface UserMapper extends BaseMapper<UserRow> {

    @Select("""
            SELECT w.id, 'WORKER' AS role, w.name, w.team, wb.building_id, NULL AS room,
                   w.phone, w.password_hash, w.active, w.created_at
            FROM t_worker w
            JOIN t_worker_building wb ON wb.worker_id = w.id
            WHERE wb.building_id = #{buildingId} AND w.active = 1
            ORDER BY wb.is_primary DESC, w.id ASC
            LIMIT 1
            """)
    UserRow findWorkerForBuilding(@Param("buildingId") String buildingId);

    @Insert("""
            INSERT INTO t_student (id,name,phone,building_id,room,password_hash,active)
            VALUES (#{id},#{name},#{phone},#{buildingId},#{room},#{password},#{active})
            """)
    int insertStudent(UserRow row);

    @Insert("""
            INSERT INTO t_worker (id,name,phone,team,password_hash,active)
            VALUES (#{id},#{name},#{phone},#{team},#{password},#{active})
            """)
    int insertWorker(UserRow row);

    @Insert("""
            INSERT INTO t_admin (id,name,phone,password_hash,active)
            VALUES (#{id},#{name},#{phone},#{password},#{active})
            """)
    int insertAdmin(UserRow row);

    @Update("UPDATE t_student SET password_hash = #{hash} WHERE id = #{id}")
    int setStudentPassword(@Param("id") String id, @Param("hash") String hash);

    @Update("UPDATE t_worker SET password_hash = #{hash} WHERE id = #{id}")
    int setWorkerPassword(@Param("id") String id, @Param("hash") String hash);

    @Update("UPDATE t_admin SET password_hash = #{hash} WHERE id = #{id}")
    int setAdminPassword(@Param("id") String id, @Param("hash") String hash);
}
