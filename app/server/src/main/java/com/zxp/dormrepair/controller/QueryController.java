package com.zxp.dormrepair.controller;

import com.zxp.dormrepair.common.ApiResponses;
import com.zxp.dormrepair.domain.Actor;
import com.zxp.dormrepair.dto.OrderView;
import com.zxp.dormrepair.service.ConfigService;
import com.zxp.dormrepair.service.QueryService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 只读接口。命令类接口在 {@link OrderController}。
 *
 * <p>路径与 SYS-003 对齐：/my/orders、/worker/todo、/buildings/{id}/board、/admin/*。
 */
@RestController
@RequestMapping("/api/v1")
public class QueryController {

    private final QueryService query;
    private final ConfigService config;

    public QueryController(QueryService query, ConfigService config) {
        this.query = query;
        this.config = config;
    }

    /** 我的工单：学生看自己的，师傅看派给自己的，管理端看全部 */
    @GetMapping("/my/orders")
    public Map<String, Object> myOrders(@RequestParam(required = false) String status,
                                        HttpServletRequest http) {
        List<OrderView> items = query.myOrders(actor(http), status);
        return ApiResponses.ok(Map.of("total", items.size(), "items", items));
    }

    /** 师傅工作台：按楼栋、楼层从高到低 */
    @GetMapping("/worker/todo")
    public Map<String, Object> workerTodo(HttpServletRequest http) {
        return ApiResponses.ok(query.workerTodo(actor(http)));
    }

    /** 本楼清单：楼层从高到低 + 空楼层 */
    @GetMapping("/buildings/{id}/board")
    public Map<String, Object> board(@PathVariable String id,
                                     @RequestParam(required = false) List<Integer> floors) {
        return ApiResponses.ok(query.buildingBoard(id, floors == null ? List.of() : floors));
    }

    /** 四类例外 */
    @GetMapping("/admin/exceptions")
    public Map<String, Object> exceptions() {
        return ApiResponses.ok(query.exceptions());
    }

    /** 看板：零人工完成率两套口径、平均修复时长、返修率、按楼栋、计时器 */
    @GetMapping("/admin/dashboard")
    public Map<String, Object> dashboard() {
        return ApiResponses.ok(query.dashboard());
    }

    /** 可调参数。改完立刻生效，不用重启。 */
    @GetMapping("/admin/config")
    public Map<String, Object> getConfig() {
        return ApiResponses.ok(config.all());
    }

    @PutMapping("/admin/config")
    public Map<String, Object> putConfig(@RequestBody Map<String, Object> patch) {
        return ApiResponses.ok(config.update(patch));
    }

    private static Actor actor(HttpServletRequest http) {
        return Actor.of(http.getHeader("X-Actor-Type"),
                http.getHeader("X-Actor-Id"),
                http.getHeader("X-Actor-Name"));
    }
}
