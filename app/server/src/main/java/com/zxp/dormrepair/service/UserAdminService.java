package com.zxp.dormrepair.service;

import com.zxp.dormrepair.auth.PasswordHasher;
import com.zxp.dormrepair.auth.UserAccount;
import com.zxp.dormrepair.auth.UserRepository;
import com.zxp.dormrepair.auth.TokenStore;
import com.zxp.dormrepair.common.BizException;
import com.zxp.dormrepair.dto.UserCreateRequest;
import com.zxp.dormrepair.statemachine.ActorType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 管理员人员维护。账号写入三张角色表，口令始终只存 PBKDF2 哈希。 */
@Service
public class UserAdminService {

    private static final java.util.Set<String> WEAK_PASSWORDS = java.util.Set.of(
            "12345678", "123456789", "1234567890", "password", "password1", "qwerty123", "admin123", "dorm1234");

    private static void validatePassword(String password) {
        if (password == null || password.length() < 12) {
            throw BizException.badRequest("口令至少 12 位");
        }
        if (WEAK_PASSWORDS.contains(password.toLowerCase(java.util.Locale.ROOT))) {
            throw BizException.badRequest("口令过于常见，请使用更难猜测的口令");
        }
    }

    private final UserRepository users;
    private final TokenStore tokens;

    public UserAdminService(UserRepository users, TokenStore tokens) {
        this.users = users;
        this.tokens = tokens;
    }

    public Map<String, Object> list() {
        List<Map<String, Object>> items = users.all().stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>(u.view());
            m.put("active", u.active());
            return m;
        }).toList();
        return Map.of("total", items.size(), "items", items);
    }

    public Map<String, Object> create(UserCreateRequest req) {
        if (req == null || req.id() == null || req.id().isBlank()
                || req.name() == null || req.name().isBlank()
                || req.password() == null) {
            throw BizException.badRequest("账号、姓名和口令必填");
        }
        validatePassword(req.password());
        String id = req.id().trim();
        if (users.find(id).isPresent()) {
            throw BizException.conflict("USER_ALREADY_EXISTS", "账号已存在：" + id);
        }
        ActorType role = ActorType.of(req.role());
        if (role != ActorType.STUDENT && role != ActorType.WORKER && role != ActorType.ADMIN) {
            throw BizException.badRequest("角色必须是 STUDENT/WORKER/ADMIN");
        }
        String building = req.building();
        if (role == ActorType.STUDENT && (building == null || building.isBlank()
                || req.room() == null || req.room().isBlank())) {
            throw BizException.badRequest("学生必须填写楼栋和房间");
        }
        if (role == ActorType.WORKER && (req.buildings() == null || req.buildings().isEmpty())
                && (building == null || building.isBlank())) {
            throw BizException.badRequest("师傅必须填写至少一个服务楼栋");
        }
        UserAccount account = new UserAccount(id, role, req.name().trim(), req.team(),
                building, req.room(), req.phone(), true, PasswordHasher.hash(req.password()));
        if (role == ActorType.WORKER) {
            List<String> buildings = req.buildings() == null || req.buildings().isEmpty()
                    ? List.of(building) : req.buildings();
            users.insertWorkerWithBuildings(account, buildings);
        } else {
            users.insert(account);
        }
        return account.view();
    }

    public Map<String, Object> setActive(String id, boolean active, String actorId) {
        UserAccount account = users.find(id)
                .orElseThrow(() -> BizException.notFound("账号不存在：" + id));
        if (!active) {
            if (account.id().equals(actorId)) {
                throw BizException.conflict("CANNOT_DISABLE_SELF", "不能停用当前登录的管理员账号");
            }
        }
        boolean changed = account.active() != active;
        if (changed) {
            if (!active && account.role() == ActorType.ADMIN) {
                if (!users.disableAdminIfNotLast(id)) {
                    throw BizException.conflict("LAST_ACTIVE_ADMIN", "不能停用最后一个启用的管理员");
                }
            } else {
                users.setActive(id, active);
            }
        }
        int revokedSessions = active ? 0 : tokens.revokeAccount(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("active", active);
        result.put("changed", changed);
        result.put("revokedSessions", revokedSessions);
        return result;
    }


    public Map<String, Object> resetPassword(String id, String password) {
        validatePassword(password);
        users.find(id).orElseThrow(() -> BizException.notFound("账号不存在：" + id));
        users.setPassword(id, PasswordHasher.hash(password));
        int revokedSessions = tokens.revokeAccount(id);
        return Map.of("id", id, "passwordReset", true, "revokedSessions", revokedSessions);
    }
}
