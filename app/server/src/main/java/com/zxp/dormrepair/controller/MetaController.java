package com.zxp.dormrepair.controller;

import com.zxp.dormrepair.common.ApiResponses;
import com.zxp.dormrepair.common.TimeUtil;
import com.zxp.dormrepair.statemachine.ActorType;
import com.zxp.dormrepair.statemachine.OrderEvent;
import com.zxp.dormrepair.statemachine.OrderStatus;
import com.zxp.dormrepair.statemachine.StateMachine;
import com.zxp.dormrepair.statemachine.TransitionTable;
import com.zxp.dormrepair.service.AiClient;
import com.zxp.dormrepair.service.ConfigService;
import com.zxp.dormrepair.service.TimerService;
import com.zxp.dormrepair.service.SeedService;
import com.zxp.dormrepair.store.OrderRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final AiClient ai;
    private final ConfigService config;
    private final TimerService timers;

    public MetaController(OrderRepository store, SeedService seeder, AiClient ai,
                          ConfigService config, TimerService timers) {
        this.store = store;
        this.seeder = seeder;
        this.ai = ai;
        this.config = config;
        this.timers = timers;
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
        // 三个阈值从 t_config 读，读不到有默认值 —— 别再写死第二份
        m.put("budgetLine", config.budgetLine());
        m.put("verifyHours", config.verifyHours());
        m.put("archiveDays", config.archiveDays());
        // 界面要用的静态字典。放这里是为了页面里一个中文常量都不用自己抄第二遍（C-14）
        m.put("brand", "宿舍维修系统");
        m.put("version", "V5.0");
        m.put("buildings", List.of(
                Map.of("id", "B7", "name", "东区7号楼", "floors", List.of(5, 4, 3, 2)),
                Map.of("id", "B8", "name", "东区8号楼", "floors", List.of(3, 2, 1))));
        m.put("noShowReasons", List.of(
                Map.of("code", "KNOCK_NO_ANSWER", "label", "敲门没人应"),
                Map.of("code", "CALL_UNREACHABLE", "label", "打电话没人接"),
                Map.of("code", "STUDENT_NOT_IN", "label", "学生不在宿舍"),
                Map.of("code", "DOOR_LOCKED", "label", "门锁着进不去")));
        m.put("finishTags", List.of("更换软管", "重新打胶", "紧固螺丝", "疏通管道",
                "更换阀芯", "更换面板模块", "调节校正", "清洁现场"));
        m.put("cancelReasons", List.of("不需要修了", "重复报修", "自己已经修好", "信息填错"));
        m.put("exceptionKinds", List.of(
                Map.of("code", "MISMATCH", "label", "数据对不上"),
                Map.of("code", "OVER_BUDGET", "label", "超预算采购"),
                Map.of("code", "REPEAT_REWORK", "label", "同一单反复返修"),
                Map.of("code", "CANCEL_REQUEST", "label", "学生申请撤销")));
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

    /** AI 在不在。别等学生提报修了才发现定级服务挂了。 */
    @GetMapping("/ai/health")
    public Map<String, Object> aiHealth() {
        return ApiResponses.ok(ai.health());
    }

    /**
     * 演示用：把时钟往前推若干小时，立刻跑一遍定时任务。
     *
     * <p>不真等 168 小时，也不改系统时钟 —— 只是拿"未来的时刻"去和 finishAt / signAt 比。
     */
    @PostMapping("/demo/advance")
    public Map<String, Object> advance(@RequestParam(defaultValue = "168") int hours) {
        long target = TimeUtil.nowMs() + hours * 3600_000L;
        Map<String, Object> r = timers.sweep(TimeUtil.fmt(target));
        r.put("advancedHours", hours);
        return ApiResponses.ok(r);
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
