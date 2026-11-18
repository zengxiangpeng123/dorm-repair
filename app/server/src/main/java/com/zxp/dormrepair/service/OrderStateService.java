package com.zxp.dormrepair.service;

import com.zxp.dormrepair.common.BizException;
import com.zxp.dormrepair.common.TimeUtil;
import com.zxp.dormrepair.domain.Actor;
import com.zxp.dormrepair.domain.OrderEventLog;
import com.zxp.dormrepair.domain.WorkOrder;
import com.zxp.dormrepair.statemachine.OrderEvent;
import com.zxp.dormrepair.statemachine.OrderStatus;
import com.zxp.dormrepair.statemachine.StateMachine;
import com.zxp.dormrepair.store.OrderRepository;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

/**
 * 唯一能改工单状态的地方（规矩 C-01）。
 *
 * <p>顺序固定：身份 →（转移表）→ 版本 → 改字段 → 追加事件。任何一步不成立，工单一个字都不动。
 */
@Service
public class OrderStateService {

    private final OrderRepository store;

    public OrderStateService(OrderRepository store) {
        this.store = store;
    }

    public WorkOrder transit(String no, OrderEvent event, Actor actor, String note,
                             Integer expectVersion, Consumer<WorkOrder> patch) {
        WorkOrder cur = store.require(no);
        StateMachine.assertCanTrigger(event, actor.type());
        OrderStatus from = cur.getStatus();
        OrderStatus to = StateMachine.next(from, event);
        String at = TimeUtil.now();

        boolean ok = store.update(no, expectVersion, o -> {
            o.setStatus(to);
            if (patch != null) {
                patch.accept(o);
            }
            o.setStatusAt(at);
            o.getEvents().add(new OrderEventLog(
                    store.nextEventSeq(), at, event, actor.type().name(),
                    actor.id(), actor.name(), actor.type().manual(), note));
        });

        if (!ok) {
            WorkOrder latest = store.require(no);
            throw BizException.versionConflict(expectVersion, latest.getVersion(),
                    StateMachine.allowedEvents(latest.getStatus()));
        }
        return store.get(no);
    }

    /** 只留记录、不动状态的那种事件（申请撤销/申请返修/驳回返修）。 */
    public WorkOrder logOnly(String no, OrderEvent event, Actor actor, String note, Integer expectVersion) {
        return logOnly(no, event, actor, note, expectVersion, null);
    }

    public WorkOrder logOnly(String no, OrderEvent event, Actor actor, String note,
                             Integer expectVersion, java.util.function.Consumer<WorkOrder> patch) {
        WorkOrder cur = store.require(no);
        StateMachine.assertCanTrigger(event, actor.type());
        String at = TimeUtil.now();
        boolean ok = store.update(no, expectVersion, o -> {
            if (patch != null) {
                patch.accept(o);
            }
            o.getEvents().add(new OrderEventLog(
                    store.nextEventSeq(), at, event, actor.type().name(),
                    actor.id(), actor.name(), actor.type().manual(), note));
        });
        if (!ok) {
            WorkOrder latest = store.require(no);
            throw BizException.versionConflict(expectVersion, latest.getVersion(),
                    StateMachine.allowedEvents(latest.getStatus()));
        }
        return store.get(no);
    }

    /**
     * 级联副作用：返修单验收通过时，原单从"返修中"关到"已关闭"。
     *
     * <p>这条不在 14 条转移里（SYS-004 第 8 条的附带效果），所以不走 transit 的查表，
     * 改由 {@link StateMachine#nextCascade} 校验"只有返修中能被级联关闭"。
     * 事件记在学生头上（manual=1）—— 是他的验收动作导致原单关闭，不算系统自动化。
     */
    public WorkOrder cascadeClose(String no, Actor actor, String note) {
        WorkOrder cur = store.require(no);
        OrderStatus to = StateMachine.nextCascade(cur.getStatus(), OrderEvent.ORIGIN_CLOSED);
        String at = TimeUtil.now();
        boolean ok = store.update(no, cur.getVersion(), o -> {
            o.setStatus(to);
            o.setClosedAt(at);
            o.setStatusAt(at);
            o.getEvents().add(new OrderEventLog(store.nextEventSeq(), at, OrderEvent.ORIGIN_CLOSED,
                    actor.type().name(), actor.id(), actor.name(), 1, note));
        });
        if (!ok) {
            WorkOrder latest = store.require(no);
            throw BizException.versionConflict(cur.getVersion(), latest.getVersion(),
                    StateMachine.allowedEvents(latest.getStatus()));
        }
        return store.get(no);
    }
}
