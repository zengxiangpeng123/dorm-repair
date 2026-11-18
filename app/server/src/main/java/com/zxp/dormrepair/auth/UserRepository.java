package com.zxp.dormrepair.auth;

import java.util.List;
import java.util.Optional;

/**
 * 人员仓库。和工单一样做两个实现：内存（单元测试）/ MySQL（正式）。
 */
public interface UserRepository {

    Optional<UserAccount> find(String id);

    List<UserAccount> all();

    long count();

    long countByRole(com.zxp.dormrepair.statemachine.ActorType role);

    /** 自动派工候选：同时限定服务楼栋、启用状态和可选承接班组。 */
    Optional<UserAccount> findWorkerForBuilding(String buildingId, String team);

    /** 指定师傅派工时校验其服务范围，避免跨楼栋误派。 */
    boolean servesBuilding(String workerId, String buildingId);

    void insert(UserAccount user);

    /** 创建师傅并写入其服务楼栋；第一个楼栋作为主责楼栋。 */
    void insertWorkerWithBuildings(UserAccount user, java.util.List<String> buildings);

    void setPassword(String id, String passwordHash);

    /** 停用或恢复账号；实现必须按角色写入对应人员表。 */
    void setActive(String id, boolean active);

    /**
     * 原子停用管理员：仅当系统中仍有其他启用管理员时才更新。
     * 返回 false 表示目标已是最后一个启用管理员，调用方必须拒绝本次停用。
     */
    boolean disableAdminIfNotLast(String id);
}
