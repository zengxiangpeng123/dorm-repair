package com.zxp.dormrepair.statemachine;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 转移表：全站唯一的一份状态机数据。
 *
 * <p>规矩 C-02：要改状态机，只改这个文件和 {@link StateMachine}，不许在 service 里写 if-else。
 * 这里的一行一行，就是 SYS-004 第五条那 13 条转移，一条不多、一条不少。
 */
public final class TransitionTable {

    private TransitionTable() {
    }

    /** 建单那次提交：订单还不存在，所以单独记一条，不占 9 个状态的转移。 */
    public static final Map<OrderEvent, OrderStatus> CREATION =
            Collections.unmodifiableMap(new EnumMap<>(Map.of(OrderEvent.SUBMIT, OrderStatus.PENDING)));

    private static final Map<OrderStatus, Map<OrderEvent, OrderStatus>> TRANSITIONS;

    static {
        Map<OrderStatus, Map<OrderEvent, OrderStatus>> m = new EnumMap<>(OrderStatus.class);

        // 1  — SUBMIT      学生      → PENDING     建单（见 CREATION）
        m.put(OrderStatus.PENDING, row(OrderEvent.ASSIGN, OrderStatus.ASSIGNED,
                OrderEvent.CANCEL, OrderStatus.CANCELLED));                       // 2 / 12
        m.put(OrderStatus.ASSIGNED, row(OrderEvent.ARRIVE, OrderStatus.REPAIRING,
                OrderEvent.NO_SHOW, OrderStatus.PENDING,
                OrderEvent.CANCEL, OrderStatus.CANCELLED));                       // 3 / 4 / 13
        m.put(OrderStatus.REPAIRING, row(OrderEvent.FINISH, OrderStatus.VERIFYING,
                OrderEvent.NEED_PARTS, OrderStatus.PARTS));                       // 5 / 6
        m.put(OrderStatus.PARTS, row(OrderEvent.PARTS_ARRIVED, OrderStatus.PENDING)); // 7
        m.put(OrderStatus.VERIFYING, row(OrderEvent.ACCEPT, OrderStatus.DONE,
                OrderEvent.REJECT, OrderStatus.REWORKING,
                OrderEvent.AUTO_ACCEPT, OrderStatus.DONE));                       // 8 / 9 / 10
        m.put(OrderStatus.REWORKING, row());                                      // 原单挂起，新开 -R1
        m.put(OrderStatus.DONE, row(OrderEvent.ARCHIVE, OrderStatus.CLOSED));     // 11
        m.put(OrderStatus.CLOSED, row());
        m.put(OrderStatus.CANCELLED, row());

        TRANSITIONS = Collections.unmodifiableMap(m);
    }

    /** 每条事件允许谁推。CANCEL 只有学生，管理端批准撤销是另一条边（CR-001）。 */
    private static final Map<OrderEvent, Set<ActorType>> TRIGGER;

    static {
        Map<OrderEvent, Set<ActorType>> t = new EnumMap<>(OrderEvent.class);
        t.put(OrderEvent.SUBMIT, EnumSet.of(ActorType.STUDENT));
        t.put(OrderEvent.ASSIGN, EnumSet.of(ActorType.SYSTEM, ActorType.ADMIN));
        t.put(OrderEvent.ARRIVE, EnumSet.of(ActorType.WORKER));
        t.put(OrderEvent.NO_SHOW, EnumSet.of(ActorType.WORKER));
        t.put(OrderEvent.FINISH, EnumSet.of(ActorType.WORKER));
        t.put(OrderEvent.NEED_PARTS, EnumSet.of(ActorType.WORKER));
        t.put(OrderEvent.PARTS_ARRIVED, EnumSet.of(ActorType.SYSTEM, ActorType.ADMIN));
        t.put(OrderEvent.ACCEPT, EnumSet.of(ActorType.STUDENT));
        t.put(OrderEvent.REJECT, EnumSet.of(ActorType.STUDENT));
        t.put(OrderEvent.AUTO_ACCEPT, EnumSet.of(ActorType.SYSTEM));
        t.put(OrderEvent.ARCHIVE, EnumSet.of(ActorType.SYSTEM));
        t.put(OrderEvent.CANCEL, EnumSet.of(ActorType.STUDENT));
        t.put(OrderEvent.CANCEL_REQUEST, EnumSet.of(ActorType.STUDENT));
        TRIGGER = Collections.unmodifiableMap(t);
    }

    private static Map<OrderEvent, OrderStatus> row(Object... pairs) {
        Map<OrderEvent, OrderStatus> r = new EnumMap<>(OrderEvent.class);
        for (int i = 0; i < pairs.length; i += 2) {
            r.put((OrderEvent) pairs[i], (OrderStatus) pairs[i + 1]);
        }
        return r;
    }

    public static Map<OrderEvent, OrderStatus> rowOf(OrderStatus from) {
        return TRANSITIONS.getOrDefault(from, Collections.emptyMap());
    }

    public static Map<OrderStatus, Map<OrderEvent, OrderStatus>> all() {
        return TRANSITIONS;
    }

    public static Set<ActorType> triggerOf(OrderEvent event) {
        return TRIGGER.getOrDefault(event, EnumSet.noneOf(ActorType.class));
    }

    /** 13 条合法转移，按状态顺序排，给 /state/transitions 用。 */
    public static List<String[]> list() {
        List<String[]> out = new java.util.ArrayList<>();
        out.add(new String[]{"NEW", OrderEvent.SUBMIT.name(), OrderStatus.PENDING.name()});
        for (OrderStatus s : OrderStatus.values()) {
            rowOf(s).forEach((ev, to) -> out.add(new String[]{s.name(), ev.name(), to.name()}));
        }
        return out;
    }
}
