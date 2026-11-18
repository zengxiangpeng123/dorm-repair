package com.zxp.dormrepair.service;

import com.zxp.dormrepair.common.TimeUtil;
import com.zxp.dormrepair.domain.OrderEventLog;
import com.zxp.dormrepair.domain.WorkOrder;
import com.zxp.dormrepair.statemachine.OrderEvent;
import com.zxp.dormrepair.statemachine.OrderStatus;
import com.zxp.dormrepair.store.OrderRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 演示数据。内存库里直接塞对象，MySQL 里走仓储落三张表 —— 两边同一套代码。
 *
 * <p>为什么不只在 data.sql 里写：时间要相对"现在"，否则过一天打开看全是上周的单。
 * data.sql 保留给"想用 SQL 直接灌"的场合，内容与这里一致。
 */
@Service
public class SeedService {

    private final OrderRepository store;

    public SeedService(OrderRepository store) {
        this.store = store;
    }

    /** 清空后重灌 6 张单（覆盖 6 个状态）。返回灌了几张。 */
    public int reset() {
        store.clear();
        return seed();
    }

    public int seed() {
        // 1 待派工：安全类（浸水风险），AI 定紧急
        WorkOrder o1 = base(1, OrderStatus.PENDING, "B7", 4, "402", "S001", "张同学",
                "洗手盆软管一直滴水，水桶快接满了", "上午 08:00-12:00", "给排水", "紧急",
                "命中「滴水/软管」，归给排水；描述提到「快接满了」，按浸水风险算安全类，定紧急",
                null, null, 22);
        add(o1, OrderEvent.SUBMIT, "STUDENT", "S001", "张同学", 1, 22,
                "提交报修：洗手盆软管一直滴水，水桶快接满了");

        // 2 已派工：还没到门，学生这会儿还能自己撤
        WorkOrder o2 = base(2, OrderStatus.ASSIGNED, "B7", 5, "508", "S002", "李同学",
                "插座打火，有焦味，不敢用了", "下午 14:00-18:00", "电气", "紧急",
                "命中「打火/焦味」，归电气；有起火风险，按安全类定紧急",
                "W0428", "李建国", 120);
        add(o2, OrderEvent.SUBMIT, "STUDENT", "S002", "李同学", 1, 120,
                "提交报修：插座打火，有焦味，不敢用了");
        add(o2, OrderEvent.ASSIGN, "SYSTEM", "system", "系统自动派工", 0, 95, "派给 李建国（水电班）");

        // 3 维修中：已进楼，撤销边界就在这一步之后
        WorkOrder o3 = base(3, OrderStatus.REPAIRING, "B7", 3, "317", "S001", "张同学",
                "水龙头关不严，一直滴", "全天（08:00-22:00）", "给排水", "较急",
                "命中「水龙头/关不严」，归给排水；不影响使用但浪费水，定较急",
                "W0428", "李建国", 240);
        add(o3, OrderEvent.SUBMIT, "STUDENT", "S001", "张同学", 1, 240, "提交报修：水龙头关不严，一直滴");
        add(o3, OrderEvent.ASSIGN, "SYSTEM", "system", "系统自动派工", 0, 180, "派给 李建国（水电班）");
        add(o3, OrderEvent.ARRIVE, "WORKER", "W0428", "李建国", 1, 40, "到门打卡");
        o3.setArriveAt(TimeUtil.agoMinutes(40));

        // 4 待配件：预算内 38 元
        WorkOrder o4 = base(4, OrderStatus.PARTS, "B7", 2, "217", "S002", "李同学",
                "卫生间下水慢，怀疑堵了", "晚上 18:00-22:00", "给排水", "普通",
                "命中「下水慢/堵」，归给排水；不影响基本生活，定普通",
                "W0428", "李建国", 360);
        add(o4, OrderEvent.SUBMIT, "STUDENT", "S002", "李同学", 1, 360, "提交报修：卫生间下水慢，怀疑堵了");
        add(o4, OrderEvent.ASSIGN, "SYSTEM", "system", "系统自动派工", 0, 300, "派给 李建国（水电班）");
        add(o4, OrderEvent.ARRIVE, "WORKER", "W0428", "李建国", 1, 120, "到门打卡");
        add(o4, OrderEvent.NEED_PARTS, "WORKER", "W0428", "李建国", 1, 90, "缺件：阀芯 ¥38");
        o4.setArriveAt(TimeUtil.agoMinutes(120));
        o4.setPartsName("阀芯");
        o4.setPartsAmount(new BigDecimal("38.00"));
        o4.setPartsEta(TimeUtil.dateAfterDays(1));

        // 5 待验收：T1 从完工那刻起算 168 小时
        WorkOrder o5 = base(5, OrderStatus.VERIFYING, "B7", 4, "402", "S001", "张同学",
                "空调不制冷，出热风", "上午 08:00-12:00", "电器", "普通",
                "命中「空调/不制冷」，归电器；不影响基本生活，定普通",
                "W0428", "李建国", 1560);
        add(o5, OrderEvent.SUBMIT, "STUDENT", "S001", "张同学", 1, 1560, "提交报修：空调不制冷，出热风");
        add(o5, OrderEvent.ASSIGN, "SYSTEM", "system", "系统自动派工", 0, 1500, "派给 李建国（水电班）");
        add(o5, OrderEvent.ARRIVE, "WORKER", "W0428", "李建国", 1, 1440, "到门打卡");
        add(o5, OrderEvent.FINISH, "WORKER", "W0428", "李建国", 1, 60, "完工提交：清洗滤网，加氟");
        o5.setArriveAt(TimeUtil.agoMinutes(1440));
        o5.setFinishAt(TimeUtil.agoMinutes(60));
        o5.setPhotosAfter(new ArrayList<>(List.of("demo://after/402.jpg")));

        // 6 已完成：T2 从签字那刻起算 7 天
        WorkOrder o6 = base(6, OrderStatus.DONE, "B8", 2, "205", "S003", "王同学",
                "窗户把手松了，关不严", "下午 14:00-18:00", "门窗家具", "普通",
                "命中「窗户/把手」，归门窗家具；不影响基本生活，定普通",
                "W0512", "王海涛", 2880);
        add(o6, OrderEvent.SUBMIT, "STUDENT", "S003", "王同学", 1, 2880, "提交报修：窗户把手松了，关不严");
        add(o6, OrderEvent.ASSIGN, "SYSTEM", "system", "系统自动派工", 0, 2820, "派给 王海涛（综合班）");
        add(o6, OrderEvent.ARRIVE, "WORKER", "W0512", "王海涛", 1, 2760, "到门打卡");
        add(o6, OrderEvent.FINISH, "WORKER", "W0512", "王海涛", 1, 2700, "完工提交：紧固把手，上润滑油");
        add(o6, OrderEvent.ACCEPT, "STUDENT", "S003", "王同学", 1, 2640, "验收通过，签字人 王同学");
        o6.setArriveAt(TimeUtil.agoMinutes(2760));
        o6.setFinishAt(TimeUtil.agoMinutes(2700));
        o6.setSignAt(TimeUtil.agoMinutes(2640));
        o6.setPhotosAfter(new ArrayList<>(List.of("demo://after/205.jpg")));

        int n = 0;
        for (WorkOrder o : List.of(o1, o2, o3, o4, o5, o6)) {
            o.setVersion(Math.max(1, o.getEvents().size()));
            o.setStatusAt(o.getEvents().isEmpty() ? o.getCreatedAt()
                    : o.getEvents().get(o.getEvents().size() - 1).at());
            store.save(o);
            n++;
        }
        return n;
    }

    // ------------------------------------------------------------------

    private WorkOrder base(int seq, OrderStatus status, String building, int floor, String room,
                           String studentId, String studentName, String desc, String window,
                           String category, String level, String aiBasis, String workerId,
                           String workerName, int createdAgoMinutes) {
        WorkOrder o = new WorkOrder();
        o.setNo(no(seq));
        o.setStatus(status);
        o.setBuilding(building);
        o.setFloor(floor);
        o.setRoom(room);
        o.setStudentId(studentId);
        o.setStudentName(studentName);
        o.setDescription(desc);
        o.setVisitWindow(window);
        o.setPhotosBefore(new ArrayList<>(List.of("demo://before/" + room + ".jpg")));
        o.setPhotosAfter(new ArrayList<>());
        o.setCategory(category);
        o.setLevel(level);
        o.setLevelSource("AI");
        o.setAiBasis(aiBasis);
        o.setWorkerId(workerId);
        o.setWorkerName(workerName);
        o.setCreatedAt(TimeUtil.agoMinutes(createdAgoMinutes));
        o.setEvents(new ArrayList<>());
        return o;
    }

    private void add(WorkOrder o, OrderEvent event, String actorType, String actorId, String actorName,
                     int manual, int agoMinutes, String note) {
        o.getEvents().add(new OrderEventLog(0L, TimeUtil.agoMinutes(agoMinutes), event, actorType,
                actorId, actorName, manual, note));
    }

    /** 单号按当天日期 + 序号，跟正式派号一个格式，别把演示数据写成一眼假的串。 */
    private String no(int i) {
        String day = LocalDate.now(TimeUtil.ZONE).toString().replace("-", "");
        return "BX" + day + String.format("%03d", i);
    }
}
