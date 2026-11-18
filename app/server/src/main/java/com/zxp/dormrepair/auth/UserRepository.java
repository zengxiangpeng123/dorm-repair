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

    Optional<UserAccount> findWorkerForBuilding(String buildingId);

    /** 指定师傅派工时校验其服务范围，避免跨楼栋误派。 */
    boolean servesBuilding(String workerId, String buildingId);

    void insert(UserAccount user);

    void setPassword(String id, String passwordHash);
}
