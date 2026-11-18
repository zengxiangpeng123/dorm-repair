package com.zxp.dormrepair.statemachine;

import com.zxp.dormrepair.common.BizException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 状态机本身：只回答两个问题 —— 这一步能不能走、走到哪。不碰数据库、不碰业务。
 */
public final class StateMachine {

    private StateMachine() {
    }

    /** 建单时唯一能走的一步。 */
    public static OrderStatus onCreation(OrderEvent event) {
        OrderStatus to = TransitionTable.CREATION.get(event);
        if (to == null) {
            throw BizException.badRequest("建单只接受 SUBMIT");
        }
        return to;
    }

    /**
     * 从 from 推 event 会到哪个状态。走不通就抛 409，并把"现在能做哪些"一起带回去。
     */
    public static OrderStatus next(OrderStatus from, OrderEvent event) {
        OrderStatus to = TransitionTable.rowOf(from).get(event);
        if (to == null) {
            throw BizException.stateConflict(
                    "状态「" + from.label() + "」不接受事件「" + event.label() + "」",
                    from.name(), event.name(), allowedEvents(from));
        }
        return to;
    }

    /** 这个状态下能推哪些事件（顺序固定，前端照着画按钮）。 */
    public static List<String> allowedEvents(OrderStatus from) {
        List<String> out = new ArrayList<>();
        for (OrderEvent ev : TransitionTable.rowOf(from).keySet()) {
            out.add(ev.name());
        }
        return out;
    }

    /** 身份对不对。不对抛 403，不是 409 —— 两件事要分开。 */
    public static void assertCanTrigger(OrderEvent event, ActorType type) {
        if (!TransitionTable.triggerOf(event).contains(type)) {
            throw BizException.forbidden("事件「" + event.label() + "」不能由 " + type.name() + " 触发");
        }
    }

    /** 10 行 × 13 种事件，供 /state/matrix 自检用。 */
    public static Map<String, Object> matrix() {
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> froms = new ArrayList<>();
        froms.add("NEW");
        for (OrderStatus s : OrderStatus.values()) {
            froms.add(s.name());
        }
        int legal = 0;
        for (String from : froms) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("from", from);
            List<Map<String, Object>> cells = new ArrayList<>();
            for (OrderEvent ev : TransitionTable.EVENTS) {
                String to = null;
                if ("NEW".equals(from)) {
                    OrderStatus t = TransitionTable.CREATION.get(ev);
                    to = t == null ? null : t.name();
                } else {
                    OrderStatus t = TransitionTable.rowOf(OrderStatus.valueOf(from)).get(ev);
                    to = t == null ? null : t.name();
                }
                Map<String, Object> cell = new LinkedHashMap<>();
                cell.put("event", ev.name());
                cell.put("to", to);
                cell.put("legal", to != null);
                cells.add(cell);
                if (to != null) {
                    legal++;
                }
            }
            row.put("cells", cells);
            rows.add(row);
        }
        int total = rows.size() * TransitionTable.EVENTS.size();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rows", rows);
        out.put("rowCount", rows.size());
        out.put("eventCount", TransitionTable.EVENTS.size());
        out.put("total", total);
        out.put("legal", legal);
        return out;
    }

    /**
     * 级联副作用：返修单验收通过时，原单从"返修中"关到"已关闭"。
     *
     * <p>它不占 14 条转移，也不许被当成命令调用 —— 只由 {@code OrderService.accept} 在验过
     * "这是一张返修单" 之后显式调用一次。
     */
    public static OrderStatus nextCascade(OrderStatus from, OrderEvent cascade) {
        Map<OrderStatus, OrderStatus> m = TransitionTable.cascades().get(cascade.name());
        OrderStatus to = m == null ? null : m.get(from);
        if (to == null) {
            throw BizException.stateConflict(
                    "状态「" + from.label() + "」没有级联「" + cascade.label() + "」",
                    from.name(), cascade.name(), allowedEvents(from));
        }
        return to;
    }
}
