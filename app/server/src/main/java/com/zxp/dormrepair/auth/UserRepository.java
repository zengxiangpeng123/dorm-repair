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

    Optional<UserAccount> findWorkerForBuilding(String buildingId);

    void insert(UserAccount user);

    void setPassword(String id, String passwordHash);
}
