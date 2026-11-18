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

    /** 登录响应里的师傅主责楼栋；只取 is_primary=1，避免把多楼栋支持误当成单一默认楼栋。 */
    @Select("""
            SELECT wb.building_id
            FROM t_worker_building wb
            JOIN t_worker w ON w.id = wb.worker_id
            WHERE wb.worker_id = #{workerId} AND wb.is_primary = 1 AND w.active = 1
            ORDER BY wb.building_id ASC
            LIMIT 1
            """)
    String findPrimaryBuilding(@Param("workerId") String workerId);

    @Select("""
            SELECT
              (SELECT COUNT(*) FROM t_student WHERE #{role}='STUDENT') +
              (SELECT COUNT(*) FROM t_worker  WHERE #{role}='WORKER') +
              (SELECT COUNT(*) FROM t_admin   WHERE #{role}='ADMIN')
            """)
    Long countByRole(@Param("role") String role);

    @Select("""
            SELECT COUNT(*)
            FROM t_worker_building wb
            JOIN t_worker w ON w.id = wb.worker_id
            WHERE wb.worker_id = #{workerId}
              AND wb.building_id = #{buildingId}
              AND w.active = 1
            """)
    Integer countWorkerBuilding(@Param("workerId") String workerId,
                                @Param("buildingId") String buildingId);

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

    @Insert("""
            INSERT INTO t_worker_building (worker_id,building_id,is_primary)
            VALUES (#{workerId},#{buildingId},#{primary})
            """)
    int insertWorkerBuilding(@Param("workerId") String workerId,
                             @Param("buildingId") String buildingId,
                             @Param("primary") boolean primary);

    @Update("UPDATE t_student SET password_hash = #{hash} WHERE id = #{id}")
    int setStudentPassword(@Param("id") String id, @Param("hash") String hash);

    @Update("UPDATE t_worker SET password_hash = #{hash} WHERE id = #{id}")
    int setWorkerPassword(@Param("id") String id, @Param("hash") String hash);

    @Update("UPDATE t_admin SET password_hash = #{hash} WHERE id = #{id}")
    int setAdminPassword(@Param("id") String id, @Param("hash") String hash);

    @Update("UPDATE t_student SET active = #{active} WHERE id = #{id}")
    int setStudentActive(@Param("id") String id, @Param("active") boolean active);

    @Update("UPDATE t_worker SET active = #{active} WHERE id = #{id}")
    int setWorkerActive(@Param("id") String id, @Param("active") boolean active);

    @Update("UPDATE t_admin SET active = #{active} WHERE id = #{id}")
    int setAdminActive(@Param("id") String id, @Param("active") boolean active);

    /** 条件更新把“至少还有一个其他启用管理员”放进同一语句，避免并发双停用。 */
    @Update("""
            UPDATE t_admin
            SET active = 0
            WHERE id = #{id}
              AND active = 1
              AND EXISTS (
                SELECT 1 FROM (
                  SELECT id FROM t_admin WHERE active = 1 AND id <> #{id} LIMIT 1
                ) AS another_active_admin
              )
            """)
    int disableAdminIfNotLast(@Param("id") String id);
}
