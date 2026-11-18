package com.zxp.dormrepair.auth;

import com.zxp.dormrepair.statemachine.ActorType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 内存人员表。跑 mvn test 时不连数据库，六个演示账号写死在这里，口令哈希是同一份。
 */
@Component
@ConditionalOnProperty(name = "app.store", havingValue = "memory", matchIfMissing = true)
public class InMemoryUserRepository implements UserRepository {

    private final Map<String, UserAccount> users = new ConcurrentHashMap<>();
    private final Map<String, java.util.List<String>> workerBuildings = new ConcurrentHashMap<>();

    public InMemoryUserRepository() {
        String pw = PasswordHasher.demoHash();
        insert(new UserAccount("S001", ActorType.STUDENT, "张同学", null, "B7", "402", "13800000001", true, pw));
        insert(new UserAccount("S002", ActorType.STUDENT, "李同学", null, "B7", "508", "13800000002", true, pw));
        insert(new UserAccount("S003", ActorType.STUDENT, "王同学", null, "B8", "205", "13800000003", true, pw));
        insert(new UserAccount("W0428", ActorType.WORKER, "李建国", "水电班", "B7", null, "13900000428", true, pw));
        insert(new UserAccount("W0512", ActorType.WORKER, "王海涛", "综合班", "B8", null, "13900000512", true, pw));
        insert(new UserAccount("A001", ActorType.ADMIN, "王主任", null, null, null, "13700000001", true, pw));
    }

    @Override
    public Optional<UserAccount> find(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(users.get(id));
    }

    @Override
    public List<UserAccount> all() {
        return users.values().stream().sorted((a, b) -> a.id().compareTo(b.id())).toList();
    }

    @Override
    public long count() {
        return users.size();
    }

    @Override
    public long countByRole(ActorType role) {
        return users.values().stream().filter(u -> u.role() == role).count();
    }

    @Override
    public Optional<UserAccount> findWorkerForBuilding(String buildingId, String team) {
        java.util.Set<String> served = workerBuildings.entrySet().stream()
                .filter(e -> e.getValue().contains(buildingId))
                .map(java.util.Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
        return users.values().stream()
                .filter(u -> u.role() == ActorType.WORKER && buildingId != null
                        && (buildingId.equals(u.building()) || served.contains(u.id())) && u.active()
                        && (team == null || team.isBlank() || team.equals(u.team())))
                .sorted((a, b) -> a.id().compareTo(b.id()))
                .findFirst();
    }

    @Override
    public boolean servesBuilding(String workerId, String buildingId) {
        UserAccount worker = users.get(workerId);
        java.util.List<String> served = workerBuildings.get(workerId);
        return worker != null && worker.role() == ActorType.WORKER && worker.active()
                && buildingId != null
                && (buildingId.equals(worker.building()) || (served != null && served.contains(buildingId)));
    }

    @Override
    public void insert(UserAccount user) {
        users.put(user.id(), user);
    }

    /** 测试专用：模拟生产空库，避免依赖演示账号。 */
    public void clear() {
        users.clear();
        workerBuildings.clear();
    }

    @Override
    public void insertWorkerWithBuildings(UserAccount user, java.util.List<String> buildings) {
        users.put(user.id(), user);
        workerBuildings.put(user.id(), new java.util.ArrayList<>(buildings));
    }

    @Override
    public void setPassword(String id, String passwordHash) {
        users.computeIfPresent(id, (k, u) -> new UserAccount(u.id(), u.role(), u.name(), u.team(),
                u.building(), u.room(), u.phone(), u.active(), passwordHash));
    }

    @Override
    public void setActive(String id, boolean active) {
        users.computeIfPresent(id, (k, u) -> new UserAccount(u.id(), u.role(), u.name(), u.team(),
                u.building(), u.room(), u.phone(), active, u.password()));
    }

    @Override
    public synchronized boolean disableAdminIfNotLast(String id) {
        long activeAdmins = users.values().stream()
                .filter(u -> u.role() == ActorType.ADMIN && u.active())
                .count();
        UserAccount target = users.get(id);
        if (target == null || target.role() != ActorType.ADMIN
                || !target.active() || activeAdmins <= 1) {
            return false;
        }
        setActive(id, false);
        return true;
    }
}
