package com.zxp.dormrepair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxp.dormrepair.common.BizException;
import com.zxp.dormrepair.statemachine.ActorType;
import com.zxp.dormrepair.statemachine.OrderEvent;
import com.zxp.dormrepair.statemachine.OrderStatus;
import com.zxp.dormrepair.statemachine.StateMachine;
import com.zxp.dormrepair.statemachine.TransitionTable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 白盒单测：状态机本身。 老师要求「至少一个模块的详细白盒单测」，这个模块就是它。
 *
 * <p>14 条合法边一条一条点名，117 条非法组合一条一条拒，谁改坏了这里立刻红。
 */
class StateMachineTest {

    /** 当前 14 条合法转移，写死在这里当对照，代码改了测试就红。 */
    private static final List<String[]> LEGAL = List.of(
            new String[]{"NEW", "SUBMIT", "PENDING"},
            new String[]{"PENDING", "ASSIGN", "ASSIGNED"},
            new String[]{"ASSIGNED", "ARRIVE", "REPAIRING"},
            new String[]{"ASSIGNED", "NO_SHOW", "PENDING"},
            new String[]{"REPAIRING", "FINISH", "VERIFYING"},
            new String[]{"REPAIRING", "NEED_PARTS", "PARTS"},
            new String[]{"PARTS", "PARTS_ARRIVED", "PENDING"},
            new String[]{"VERIFYING", "ACCEPT", "DONE"},
            new String[]{"VERIFYING", "REJECT", "REWORKING"},
            new String[]{"VERIFYING", "AUTO_ACCEPT", "DONE"},
            new String[]{"DONE", "ARCHIVE", "CLOSED"},
            new String[]{"PENDING", "CANCEL", "CANCELLED"},
            new String[]{"ASSIGNED", "CANCEL", "CANCELLED"},
            new String[]{"REPAIRING", "ADMIN_CANCEL", "CANCELLED"});

    @Test
    @DisplayName("14 条合法转移，一条不多一条不少")
    void legalTransitions() {
        int count = 0;
        for (String[] t : LEGAL) {
            OrderStatus from = "NEW".equals(t[0]) ? null : OrderStatus.valueOf(t[0]);
            OrderEvent ev = OrderEvent.valueOf(t[1]);
            OrderStatus expected = OrderStatus.valueOf(t[2]);
            OrderStatus actual = from == null ? StateMachine.onCreation(ev) : StateMachine.next(from, ev);
            assertEquals(expected, actual, t[0] + " --" + t[1] + "-->");
            count++;
        }
        assertEquals(14, count);
    }

    @Test
    @DisplayName("转移表正好 14 条，管理员撤销审批复用 CANCEL 边")
    void tableSizeIsThirteen() {
        assertEquals(14, TransitionTable.list().size());
    }

    @Test
    @DisplayName("96 条非法组合全部 409，且带着 allowed 列表（级联事件不在其中）")
    void illegalCombinations() {
        int illegal = 0;
        for (OrderEvent ev : TransitionTable.EVENTS) {
            if (!ev.changesStatus()) {
                continue;
            }
            for (OrderStatus s : OrderStatus.values()) {
                boolean legal = TransitionTable.rowOf(s).containsKey(ev);
                if (legal) {
                    continue;
                }
                BizException e = assertThrows(BizException.class, () -> StateMachine.next(s, ev),
                        s + " 不该接受 " + ev);
                assertEquals(409, e.status().value());
                assertEquals("STATE_CONFLICT", e.code());
                assertEquals(s.name(), e.extra().get("from"));
                assertTrue(e.extra().get("allowed") instanceof List, "allowed 必须是数组");
                illegal++;
            }
        }
        // 9 状态 × 12 个会改状态的事件 = 108 组合，其中 12 条合法（NEW 那条不算在内）
        assertEquals(9 * 13 - 13, illegal);
    }

    @Test
    @DisplayName("矩阵：10 行（含 NEW）× 13 事件，合法 14")
    void matrix() {
        Map<String, Object> m = StateMachine.matrix();
        assertEquals(10, m.get("rowCount"));
        assertEquals(14, m.get("eventCount"));
        assertEquals(140, m.get("total"));
        assertEquals(14, m.get("legal"));
    }

    @Test
    @DisplayName("谁能推哪条事件：CANCEL 允许学生与管理员，PARTS_ARRIVED 没有师傅")
    void triggers() {
        assertEquals(List.of(ActorType.STUDENT), List.copyOf(TransitionTable.triggerOf(OrderEvent.CANCEL)));
        assertEquals(List.of(ActorType.ADMIN), List.copyOf(TransitionTable.triggerOf(OrderEvent.ADMIN_CANCEL)));
        assertTrue(TransitionTable.triggerOf(OrderEvent.PARTS_ARRIVED).contains(ActorType.ADMIN));
        assertTrue(!TransitionTable.triggerOf(OrderEvent.PARTS_ARRIVED).contains(ActorType.WORKER));
        assertTrue(!TransitionTable.triggerOf(OrderEvent.ASSIGN).contains(ActorType.STUDENT));

        BizException e = assertThrows(BizException.class,
                () -> StateMachine.assertCanTrigger(OrderEvent.ASSIGN, ActorType.STUDENT));
        assertEquals(403, e.status().value());
    }

    @Test
    @DisplayName("终态出不去：已关闭、已取消没有任何边")
    void terminalStates() {
        assertTrue(OrderStatus.CLOSED.terminal());
        assertTrue(OrderStatus.CANCELLED.terminal());
        for (OrderEvent ev : TransitionTable.EVENTS) {
            if (!ev.changesStatus()) {
                continue;
            }
            assertThrows(BizException.class, () -> StateMachine.next(OrderStatus.CLOSED, ev));
            assertThrows(BizException.class, () -> StateMachine.next(OrderStatus.CANCELLED, ev));
        }
    }

    /** 反向检查：LEGAL 里出现的每条边，转移表都要有；表里有的，LEGAL 也要有。 */
    @Test
    @DisplayName("测试里的 14 条与代码里的 14 条完全对齐")
    void legalListMatchesTable() {
        List<String> fromTable = new ArrayList<>();
        for (String[] t : TransitionTable.list()) {
            fromTable.add(String.join(">", t));
        }
        List<String> fromTest = new ArrayList<>();
        for (String[] t : LEGAL) {
            fromTest.add(String.join(">", t));
        }
        fromTable.sort(String::compareTo);
        fromTest.sort(String::compareTo);
        assertEquals(fromTest, fromTable);
    }
}
