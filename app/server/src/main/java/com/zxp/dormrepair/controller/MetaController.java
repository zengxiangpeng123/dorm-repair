package com.zxp.dormrepair.controller;

import com.zxp.dormrepair.common.ApiResponses;
import com.zxp.dormrepair.statemachine.ActorType;
import com.zxp.dormrepair.statemachine.OrderEvent;
import com.zxp.dormrepair.statemachine.OrderStatus;
import com.zxp.dormrepair.statemachine.StateMachine;
import com.zxp.dormrepair.statemachine.TransitionTable;
import com.zxp.dormrepair.service.SeedService;
import com.zxp.dormrepair.store.OrderRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 元信息与自检：让前端不用把状态词、阈值写死；让答辩能当场把 120 种组合摊开看。
 */
@RestController
@RequestMapping("/api/v1")
public class MetaController {

    private final OrderRepository store;
    private final SeedService seeder;

    public MetaController(OrderRepository store, SeedService seeder) {
        this.store = store;
        this.seeder = seeder;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return ApiResponses.ok(Map.of("status", "UP", "orders", store.size()));
    }

    @GetMapping("/meta")
    public Map<String, Object> meta() {
        List<Map<String, Object>> statuses = new ArrayList<>();
        for (OrderStatus s : OrderStatus.values()) {
            statuses.add(Map.of("code", s.name(), "label", s.label(), "terminal", s.terminal()));
        }
        List<Map<String, Object>> events = new ArrayList<>();
        for (OrderEvent e : OrderEvent.values()) {
            List<String> trigger = TransitionTable.triggerOf(e).stream().map(ActorType::name).toList();
            events.add(Map.of("code", e.name(), "label", e.label(), "trigger", trigger));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statuses", statuses);
        m.put("events", events);
        m.put("transitions", TransitionTable.list().size());
        m.put("categories", List.of("给排水", "电气", "电器", "门窗家具", "网络"));
        m.put("levels", List.of("紧急", "较急", "普通"));
        m.put("windows", List.of("全天（08:00-22:00）", "上午 08:00-12:00",
                "下午 14:00-18:00", "晚上 18:00-22:00"));
        m.put("budgetLine", 200);
        m.put("verifyHours", 168);
        m.put("archiveDays", 7);
        return ApiResponses.ok(m);
    }

    @GetMapping("/state/matrix")
    public Map<String, Object> matrix() {
        return ApiResponses.ok(StateMachine.matrix());
    }

    @GetMapping("/state/transitions")
    public Map<String, Object> transitions() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (String[] t : TransitionTable.list()) {
            OrderEvent ev = OrderEvent.valueOf(t[1]);
            items.add(Map.of(
                    "from", t[0],
                    "event", t[1],
                    "eventLabel", ev.label(),
                    "to", t[2],
                    "trigger", TransitionTable.triggerOf(ev).stream().map(ActorType::name).toList()));
        }
        return ApiResponses.ok(Map.of("items", items, "total", items.size()));
    }

    /** 演示用：清空再重灌一套演示数据（内存库和 MySQL 同一个动作）。 */
    @PostMapping("/demo/reset")
    public Map<String, Object> reset() {
        int before = store.size();
        int after = seeder.reset();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("deleted", before);
        m.put("seeded", after);
        m.put("orders", store.size());
        return ApiResponses.ok(m);
    }
}
