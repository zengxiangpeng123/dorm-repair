package com.zxp.dormrepair.controller;

import com.zxp.dormrepair.common.ActorResolver;
import com.zxp.dormrepair.common.ApiResponses;
import com.zxp.dormrepair.dto.OrderView;
import com.zxp.dormrepair.service.ConfigService;
import com.zxp.dormrepair.service.QueryService;
import com.zxp.dormrepair.statemachine.ActorType;
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
 * 每个接口都要带令牌，身份也要对得上：管理端那几个接口学生和师傅进来是 403。
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
                                        @RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "200") int pageSize,
                                        HttpServletRequest http) {
        var actor = ActorResolver.require(ActorResolver.resolve(http),
                ActorType.STUDENT, ActorType.WORKER, ActorType.ADMIN);
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(pageSize, 200));
        var result = query.myOrders(actor, status, safePage, safeSize);
        List<OrderView> items = result.items().stream().map(OrderView::of).toList();
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("items", items);
        out.put("total", result.total());
        out.put("page", result.page());
        out.put("pageSize", result.pageSize());
        out.put("hasMore", (long) result.page() * result.pageSize() < result.total());
        return ApiResponses.ok(out);
    }

    /** 师傅工作台：按楼栋、楼层从高到低。管理端也能看，学生看不到。 */
    @GetMapping("/worker/todo")
    public Map<String, Object> workerTodo(HttpServletRequest http) {
        var actor = ActorResolver.require(ActorResolver.resolve(http),
                ActorType.WORKER, ActorType.ADMIN);
        return ApiResponses.ok(query.workerTodo(actor));
    }

    /** 本楼清单：楼层从高到低 + 空楼层 */
    @GetMapping("/buildings/{id}/board")
    public Map<String, Object> board(@PathVariable String id,
                                     @RequestParam(required = false) List<Integer> floors,
                                     HttpServletRequest http) {
        ActorResolver.require(ActorResolver.resolve(http), ActorType.WORKER, ActorType.ADMIN);
        return ApiResponses.ok(query.buildingBoard(id, floors == null ? List.of() : floors));
    }

    /** 待管理员处理的返修申请。 */
    @GetMapping("/admin/rework-reviews")
    public Map<String, Object> reworkReviews(HttpServletRequest http) {
        ActorResolver.require(ActorResolver.resolve(http), ActorType.ADMIN);
        return ApiResponses.ok(query.reworkReviews());
    }

    /** AI 复核审计记录；只给管理端。 */
    @GetMapping("/admin/ai-reviews")
    public Map<String, Object> aiReviews(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(required = false) Integer pageSize,
                                         @RequestParam(required = false) Integer limit,
                                         HttpServletRequest http) {
        ActorResolver.require(ActorResolver.resolve(http), ActorType.ADMIN);
        int size = pageSize != null ? pageSize : (limit != null ? limit : 50);
        return ApiResponses.ok(query.aiReviewTasks(page, size));
    }

    /** 四类例外（只有管理端能看）；kind=ALL 或指定四类之一。 */
    @GetMapping("/admin/exceptions")
    public Map<String, Object> exceptions(@RequestParam(defaultValue = "ALL") String kind,
                                          HttpServletRequest http) {
        ActorResolver.require(ActorResolver.resolve(http), ActorType.ADMIN);
        return ApiResponses.ok(query.exceptions(kind));
    }

    /** 看板：零人工完成率两套口径、平均修复时长、返修率、按楼栋、计时器 */
    @GetMapping("/admin/dashboard")
    public Map<String, Object> dashboard(HttpServletRequest http) {
        ActorResolver.require(ActorResolver.resolve(http), ActorType.ADMIN);
        return ApiResponses.ok(query.dashboard());
    }

    /** 可调参数。改完立刻生效，不用重启。 */
    @GetMapping("/admin/config")
    public Map<String, Object> getConfig(HttpServletRequest http) {
        ActorResolver.require(ActorResolver.resolve(http), ActorType.ADMIN);
        return ApiResponses.ok(config.all());
    }

    @PutMapping("/admin/config")
    public Map<String, Object> putConfig(@RequestBody Map<String, Object> patch,
                                         HttpServletRequest http) {
        ActorResolver.require(ActorResolver.resolve(http), ActorType.ADMIN);
        return ApiResponses.ok(config.update(patch));
    }
}
