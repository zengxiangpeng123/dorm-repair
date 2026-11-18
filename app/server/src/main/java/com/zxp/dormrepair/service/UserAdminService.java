package com.zxp.dormrepair.service;

import com.zxp.dormrepair.auth.PasswordHasher;
import com.zxp.dormrepair.auth.UserAccount;
import com.zxp.dormrepair.auth.UserRepository;
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

    private final UserRepository users;

    public UserAdminService(UserRepository users) {
        this.users = users;
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
                || req.password() == null || req.password().length() < 8) {
            throw BizException.badRequest("账号、姓名和至少 8 位口令必填");
        }
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

    public Map<String, Object> resetPassword(String id, String password) {
        if (password == null || password.length() < 8) {
            throw BizException.badRequest("口令至少 8 位");
        }
        users.find(id).orElseThrow(() -> BizException.notFound("账号不存在：" + id));
        users.setPassword(id, PasswordHasher.hash(password));
        return Map.of("id", id, "passwordReset", true);
    }
}
