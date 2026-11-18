package com.zxp.dormrepair.service;

import com.zxp.dormrepair.common.BizException;
import com.zxp.dormrepair.common.TimeUtil;
import com.zxp.dormrepair.domain.Actor;
import com.zxp.dormrepair.domain.OrderEventLog;
import com.zxp.dormrepair.domain.WorkOrder;
import com.zxp.dormrepair.statemachine.OrderEvent;
import com.zxp.dormrepair.statemachine.OrderStatus;
import com.zxp.dormrepair.statemachine.StateMachine;
import com.zxp.dormrepair.store.OrderStore;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

/**
 * 唯一能改工单状态的地方（规矩 C-01）。
 *
 * <p>顺序固定：身份 →（转移表）→ 版本 → 改字段 → 追加事件。任何一步不成立，工单一个字都不动。
 */
@Service
public class OrderStateService {

    private final OrderStore store;

    public OrderStateService(OrderStore store) {
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

    /** 只留记录、不动状态的那种事件（学生申请撤销）。 */
    public WorkOrder logOnly(String no, OrderEvent event, Actor actor, String note, Integer expectVersion) {
        WorkOrder cur = store.require(no);
        StateMachine.assertCanTrigger(event, actor.type());
        String at = TimeUtil.now();
        boolean ok = store.update(no, expectVersion, o -> o.getEvents().add(new OrderEventLog(
                store.nextEventSeq(), at, event, actor.type().name(),
                actor.id(), actor.name(), actor.type().manual(), note)));
        if (!ok) {
            WorkOrder latest = store.require(no);
            throw BizException.versionConflict(expectVersion, latest.getVersion(),
                    StateMachine.allowedEvents(latest.getStatus()));
        }
        return store.get(no);
    }
}
