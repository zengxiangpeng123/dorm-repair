package com.zxp.dormrepair.controller;

import com.zxp.dormrepair.common.ActorResolver;
import com.zxp.dormrepair.common.ApiResponses;
import com.zxp.dormrepair.dto.UserCreateRequest;
import com.zxp.dormrepair.service.UserAdminService;
import com.zxp.dormrepair.statemachine.ActorType;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理员专用人员管理。 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class UserAdminController {

    private final UserAdminService service;

    public UserAdminController(UserAdminService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> list(HttpServletRequest http) {
        requireAdmin(http);
        return ApiResponses.ok(service.list());
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody UserCreateRequest req, HttpServletRequest http) {
        requireAdmin(http);
        return ApiResponses.ok(service.create(req));
    }

    @PostMapping("/{id}/active")
    public Map<String, Object> setActive(@PathVariable String id,
                                         @RequestBody Map<String, Object> body,
                                         HttpServletRequest http) {
        var actor = ActorResolver.require(ActorResolver.resolve(http), ActorType.ADMIN);
        if (body == null || !(body.get("active") instanceof Boolean active)) {
            throw com.zxp.dormrepair.common.BizException.badRequest("active 必须是布尔值");
        }
        return ApiResponses.ok(service.setActive(id, active, actor.id()));
    }

    @PostMapping("/{id}/password")
    public Map<String, Object> resetPassword(@PathVariable String id,
                                              @RequestBody Map<String, String> body,
                                              HttpServletRequest http) {
        requireAdmin(http);
        return ApiResponses.ok(service.resetPassword(id, body == null ? null : body.get("password")));
    }

    private void requireAdmin(HttpServletRequest http) {
        ActorResolver.require(ActorResolver.resolve(http), ActorType.ADMIN);
    }
}
