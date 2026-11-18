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
        return Optional.ofNullable(mapper.selectById(id)).map(MybatisUserRepository::toAccount);
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
        mapper.insert(row);
    }

    @Override
    public void setPassword(String id, String passwordHash) {
        mapper.setPasswordHash(id, passwordHash);
    }

    private static UserAccount toAccount(UserRow r) {
        return new UserAccount(r.getId(), ActorType.of(r.getRole()), r.getName(), r.getTeam(),
                r.getBuildingId(), r.getRoom(), r.getPhone(),
                r.getActive() == null || r.getActive(), r.getPassword());
    }
}
