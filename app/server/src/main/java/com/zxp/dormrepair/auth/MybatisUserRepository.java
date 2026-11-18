package com.zxp.dormrepair.auth;

import com.zxp.dormrepair.statemachine.ActorType;
import com.zxp.dormrepair.store.entity.UserRow;
import com.zxp.dormrepair.store.mapper.UserMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * MySQL 人员仓库（正式库）。表里没有口令的行会由 {@link UserSeeder} 补上默认口令的哈希。
 */
@Repository
@ConditionalOnProperty(name = "app.store", havingValue = "mysql")
public class MybatisUserRepository implements UserRepository {

    private final UserMapper mapper;

    public MybatisUserRepository(UserMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<UserAccount> find(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        UserRow row = mapper.selectById(id);
        if (row != null && "WORKER".equals(row.getRole())) {
            // t_worker 本身没有 building_id；师傅的主责楼栋在关联表里。
            row.setBuildingId(mapper.findPrimaryBuilding(id));
        }
        return Optional.ofNullable(row).map(MybatisUserRepository::toAccount);
    }

    @Override
    public List<UserAccount> all() {
        return mapper.selectList(null).stream().map(MybatisUserRepository::toAccount).toList();
    }

    @Override
    public long count() {
        Long n = mapper.selectCount(null);
        return n == null ? 0 : n;
    }

    @Override
    public long countByRole(ActorType role) {
        Long n = mapper.countByRole(role.name());
        return n == null ? 0 : n;
    }

    @Override
    public Optional<UserAccount> findWorkerForBuilding(String buildingId, String team) {
        if (buildingId == null || buildingId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.findWorkerForBuilding(buildingId, team))
                .map(MybatisUserRepository::toAccount);
    }

    @Override
    public boolean servesBuilding(String workerId, String buildingId) {
        if (workerId == null || buildingId == null) {
            return false;
        }
        Integer count = mapper.countWorkerBuilding(workerId, buildingId);
        return count != null && count > 0;
    }

    @Override
    public void insert(UserAccount u) {
        UserRow row = new UserRow();
        row.setId(u.id());
        row.setRole(u.role().name());
        row.setName(u.name());
        row.setTeam(u.team());
        row.setBuildingId(u.building());
        row.setRoom(u.room());
        row.setPhone(u.phone());
        row.setActive(u.active());
        row.setPassword(u.password());
        switch (u.role()) {
            case STUDENT -> mapper.insertStudent(row);
            case WORKER -> mapper.insertWorker(row);
            case ADMIN -> mapper.insertAdmin(row);
            default -> throw new IllegalArgumentException("不支持的人员角色：" + u.role());
        }
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void insertWorkerWithBuildings(UserAccount u, java.util.List<String> buildings) {
        UserRow row = new UserRow();
        row.setId(u.id());
        row.setRole(u.role().name());
        row.setName(u.name());
        row.setTeam(u.team());
        row.setPhone(u.phone());
        row.setActive(u.active());
        row.setPassword(u.password());
        mapper.insertWorker(row);
        for (int i = 0; i < buildings.size(); i++) {
            mapper.insertWorkerBuilding(u.id(), buildings.get(i), i == 0);
        }
    }

    @Override
    public void setPassword(String id, String passwordHash) {
        int changed;
        if (id != null && id.startsWith("W")) {
            changed = mapper.setWorkerPassword(id, passwordHash);
        } else if (id != null && id.startsWith("A")) {
            changed = mapper.setAdminPassword(id, passwordHash);
        } else {
            changed = mapper.setStudentPassword(id, passwordHash);
        }
        if (changed != 1) {
            throw new IllegalArgumentException("账号不存在：" + id);
        }
    }

    @Override
    public void setActive(String id, boolean active) {
        Optional<UserAccount> existing = find(id);
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("账号不存在：" + id);
        }
        int changed = switch (existing.get().role()) {
            case STUDENT -> mapper.setStudentActive(id, active);
            case WORKER -> mapper.setWorkerActive(id, active);
            case ADMIN -> mapper.setAdminActive(id, active);
            default -> throw new IllegalArgumentException("不支持的人员角色：" + existing.get().role());
        };
        if (changed != 1) {
            throw new IllegalArgumentException("账号不存在：" + id);
        }
    }

    @Override
    public boolean disableAdminIfNotLast(String id) {
        Optional<UserAccount> existing = find(id);
        if (existing.isEmpty() || existing.get().role() != ActorType.ADMIN) {
            return false;
        }
        return mapper.disableAdminIfNotLast(id) == 1;
    }

    private static UserAccount toAccount(UserRow r) {
        return new UserAccount(r.getId(), ActorType.of(r.getRole()), r.getName(), r.getTeam(),
                r.getBuildingId(), r.getRoom(), r.getPhone(),
                r.getActive() == null || r.getActive(), r.getPassword());
    }
}
