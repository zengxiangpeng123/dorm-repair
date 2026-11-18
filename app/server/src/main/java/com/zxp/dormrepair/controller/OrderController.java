package com.zxp.dormrepair.controller;

import com.zxp.dormrepair.common.ApiResponses;
import com.zxp.dormrepair.common.BizException;
import com.zxp.dormrepair.domain.Actor;
import com.zxp.dormrepair.domain.OrderEventLog;
import com.zxp.dormrepair.dto.OrderView;
import com.zxp.dormrepair.dto.SubmitRequest;
import com.zxp.dormrepair.dto.TransitRequest;
import com.zxp.dormrepair.service.OrderService;
import com.zxp.dormrepair.service.TimerService;
import com.zxp.dormrepair.statemachine.OrderEvent;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 12 条命令 + 三个查询。接口地址与 SYS-003 第 3 节一致。
 *
 * <p>M1 还没有登录，身份从三个请求头读（X-Actor-Type / X-Actor-Id / X-Actor-Name）。
 * M4 前换成真正的 token 鉴权，service 一行不用改。
 */
@RestController
@RequestMapping("/api/v1")
public class OrderController {

    private final OrderService orders;
    private final TimerService timers;

    public OrderController(OrderService orders, TimerService timers) {
        this.orders = orders;
        this.timers = timers;
    }

    @PostMapping("/orders")
    public ResponseEntity<Map<String, Object>> submit(@RequestBody SubmitRequest req, HttpServletRequest http) {
        OrderView view = orders.submit(req, actor(http));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponses.ok(view));
    }

    @PostMapping("/orders/{no}/assign")
    public Map<String, Object> assign(@PathVariable String no,
                                      @RequestBody(required = false) TransitRequest req,
                                      HttpServletRequest http) {
        return ApiResponses.ok(orders.assign(no, orEmpty(req), actor(http)));
    }

    @PostMapping("/orders/{no}/arrive")
    public Map<String, Object> arrive(@PathVariable String no,
                                      @RequestBody(required = false) TransitRequest req,
                                      HttpServletRequest http) {
        return ApiResponses.ok(orders.arrive(no, orEmpty(req), actor(http)));
    }

    /**
     * 楼栋级进楼打卡：界面上一次点击，背后每条 ASSIGNED 的单各生成一条 ARRIVE。
     * 已经在修的进 skipped，不让师傅为了"打个卡"被迫多按几次。
     */
    @PostMapping("/buildings/{id}/arrive")
    public Map<String, Object> arriveBuilding(@PathVariable String id, HttpServletRequest http) {
        return ApiResponses.ok(orders.arriveBuilding(id, actor(http)));
    }
    @PostMapping("/orders/{no}/no-show")
    public Map<String, Object> noShow(@PathVariable String no,
                                      @RequestBody(required = false) TransitRequest req,
                                      HttpServletRequest http) {
        return ApiResponses.ok(orders.noShow(no, orEmpty(req), actor(http)));
    }

    @PostMapping("/orders/{no}/finish")
    public Map<String, Object> finish(@PathVariable String no,
                                      @RequestBody(required = false) TransitRequest req,
                                      HttpServletRequest http) {
        return ApiResponses.ok(orders.finish(no, orEmpty(req), actor(http)));
    }

    @PostMapping("/orders/{no}/need-parts")
    public Map<String, Object> needParts(@PathVariable String no,
                                         @RequestBody(required = false) TransitRequest req,
                                         HttpServletRequest http) {
        return ApiResponses.ok(orders.needParts(no, orEmpty(req), actor(http)));
    }

    @PostMapping("/orders/{no}/parts-arrived")
    public Map<String, Object> partsArrived(@PathVariable String no,
                                            @RequestBody(required = false) TransitRequest req,
                                            HttpServletRequest http) {
        return ApiResponses.ok(orders.partsArrived(no, orEmpty(req), actor(http)));
    }

    @PostMapping("/orders/{no}/accept")
    public Map<String, Object> accept(@PathVariable String no,
                                      @RequestBody(required = false) TransitRequest req,
                                      HttpServletRequest http) {
        return ApiResponses.ok(orders.accept(no, orEmpty(req), actor(http)));
    }

    @PostMapping("/orders/{no}/reject")
    public Map<String, Object> reject(@PathVariable String no,
                                      @RequestBody(required = false) TransitRequest req,
                                      HttpServletRequest http) {
        return ApiResponses.ok(orders.reject(no, orEmpty(req), actor(http)));
    }

    @PostMapping("/orders/{no}/cancel")
    public Map<String, Object> cancel(@PathVariable String no,
                                      @RequestBody(required = false) TransitRequest req,
                                      HttpServletRequest http) {
        return ApiResponses.ok(orders.cancel(no, orEmpty(req), actor(http)));
    }

    @PostMapping("/orders/{no}/cancel-request")
    public ResponseEntity<Map<String, Object>> cancelRequest(@PathVariable String no,
                                                             @RequestBody(required = false) TransitRequest req,
                                                             HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponses.ok(orders.cancelRequest(no, orEmpty(req), actor(http))));
    }

    /** 系统到点自动验收。手动调时先判到没到点，没到点 422。 */
    @PostMapping("/orders/{no}/auto-accept")
    public Map<String, Object> autoAccept(@PathVariable String no, HttpServletRequest http) {
        Actor a = actor(http);
        if (a.type() != com.zxp.dormrepair.statemachine.ActorType.SYSTEM) {
            throw BizException.forbidden("自动验收只能由系统触发");
        }
        timers.fireOne(no, OrderEvent.AUTO_ACCEPT);
        return ApiResponses.ok(orders.detail(no));
    }

    /** 系统满期归档。手动调时同样先判到期。 */
    @PostMapping("/orders/{no}/archive")
    public Map<String, Object> archive(@PathVariable String no, HttpServletRequest http) {
        Actor a = actor(http);
        if (a.type() != com.zxp.dormrepair.statemachine.ActorType.SYSTEM) {
            throw BizException.forbidden("归档只能由系统触发");
        }
        timers.fireOne(no, OrderEvent.ARCHIVE);
        return ApiResponses.ok(orders.detail(no));
    }

    /**
     * 管理员作废（师傅已进楼、学生申请撤销）。这条边是 13 条转移里没有的第 14 条，属变更单 CR-001；
     * 没批准之前一律 501，管理端界面据此把按钮画成不可点。
     */
    @PostMapping("/orders/{no}/admin-cancel")
    public Map<String, Object> adminCancel(@PathVariable String no, HttpServletRequest http) {
        orders.detail(no);
        throw BizException.notImplemented("师傅已进楼，作废或补偿师傅要管理员定；这条边还没批准",
                "CR-001");
    }

    @GetMapping("/orders")    public Map<String, Object> list(@RequestParam(required = false) String status) {
        List<OrderView> items = orders.list(status);
        return ApiResponses.ok(Map.of("items", items, "total", items.size()));
    }

    @GetMapping("/orders/{no}")
    public Map<String, Object> detail(@PathVariable String no) {
        return ApiResponses.ok(orders.detail(no));
    }

    @GetMapping("/orders/{no}/events")
    public Map<String, Object> events(@PathVariable String no) {
        List<OrderEventLog> log = orders.detail(no).events();
        return ApiResponses.ok(Map.of("items", log, "total", log.size()));
    }

    private static Actor actor(HttpServletRequest http) {
        return Actor.of(http.getHeader("X-Actor-Type"),
                http.getHeader("X-Actor-Id"),
                http.getHeader("X-Actor-Name"));
    }

    private static TransitRequest orEmpty(TransitRequest req) {
        return req == null
                ? new TransitRequest(null, null, null, null, null, null, null, null, null, null)
                : req;
    }
}
