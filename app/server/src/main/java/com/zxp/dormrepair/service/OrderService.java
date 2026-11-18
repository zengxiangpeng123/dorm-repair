package com.zxp.dormrepair.service;

import com.zxp.dormrepair.common.BizException;
import com.zxp.dormrepair.common.TimeUtil;
import com.zxp.dormrepair.domain.Actor;
import com.zxp.dormrepair.domain.OrderEventLog;
import com.zxp.dormrepair.domain.WorkOrder;
import com.zxp.dormrepair.dto.OrderView;
import com.zxp.dormrepair.dto.SubmitRequest;
import com.zxp.dormrepair.dto.TransitRequest;
import com.zxp.dormrepair.statemachine.OrderEvent;
import com.zxp.dormrepair.statemachine.OrderStatus;
import com.zxp.dormrepair.statemachine.StateMachine;
import com.zxp.dormrepair.store.OrderStore;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 12 条命令。业务前置（照片、金额、归属）在这里判，状态怎么流转交给 {@link OrderStateService}。
 */
@Service
public class OrderService {

    private final OrderStore store;
    private final OrderStateService state;

    public OrderService(OrderStore store, OrderStateService state) {
        this.store = store;
        this.state = state;
    }

    // ---------------------------------------------------------------- 1 建单

    public OrderView submit(SubmitRequest req, Actor actor) {
        StateMachine.assertCanTrigger(OrderEvent.SUBMIT, actor.type());
        if (req.photosBefore() == null || req.photosBefore().isEmpty()) {
            throw BizException.precondition("现场照片至少 1 张", "PHOTO_REQUIRED");
        }
        String desc = req.desc() == null ? "" : req.desc().trim();
        if (desc.length() < 4) {
            throw BizException.precondition("描述至少 4 个字，不然师傅看不懂", "DESC_TOO_SHORT");
        }

        WorkOrder o = new WorkOrder();
        o.setNo(store.nextNo());
        o.setVersion(1);
        o.setStatus(StateMachine.onCreation(OrderEvent.SUBMIT));
        String at = TimeUtil.now();
        o.setBuilding(isBlank(req.building()) ? "B7" : req.building());
        o.setFloor(req.floor() == null ? 4 : req.floor());
        o.setRoom(isBlank(req.room()) ? "402" : req.room());
        o.setStudentId(actor.id());
        o.setStudentName(actor.name());
        o.setDesc(desc);
        o.setWindow(isBlank(req.window()) ? "全天（08:00-22:00）" : req.window());
        o.setPhotosBefore(req.photosBefore());
        o.setCategory(isBlank(req.category()) ? "给排水" : req.category());
        o.setLevelSource("DEFAULT");
        o.setLevel("普通");
        o.setAiBasis("M1 还没接 AI（M3 接），先按普通定级");
        o.setCreatedAt(at);
        o.setStatusAt(at);
        o.getEvents().add(new OrderEventLog(store.nextEventSeq(), at, OrderEvent.SUBMIT,
                actor.type().name(), actor.id(), actor.name(), actor.type().manual(),
                "提交报修：" + desc));
        store.save(o);
        return OrderView.of(store.get(o.getNo()));
    }

    // ------------------------------------------------------- 2 / 3 派工与到门

    public OrderView assign(String no, TransitRequest req, Actor actor) {
        String workerId = isBlank(req.workerId()) ? "W0428" : req.workerId();
        String workerName = isBlank(req.workerName()) ? "李建国" : req.workerName();
        return OrderView.of(state.transit(no, OrderEvent.ASSIGN, actor, "派给 " + workerName,
                req.version(), o -> {
                    o.setWorkerId(workerId);
                    o.setWorkerName(workerName);
                    o.setAssignedAt(TimeUtil.now());
                }));
    }

    public OrderView arrive(String no, TransitRequest req, Actor actor) {
        WorkOrder cur = store.require(no);
        if (!actor.id().equals(cur.getWorkerId())) {
            throw BizException.notOwner("这张单不是你的：" + no);
        }
        return OrderView.of(state.transit(no, OrderEvent.ARRIVE, actor, "到门打卡",
                req.version(), o -> o.setArriveAt(TimeUtil.now())));
    }

    public OrderView noShow(String no, TransitRequest req, Actor actor) {
        WorkOrder cur = store.require(no);
        if (!actor.id().equals(cur.getWorkerId())) {
            throw BizException.notOwner("这张单不是你的：" + no);
        }
        String reason = isBlank(req.reason()) ? "KNOCK_NO_ANSWER" : req.reason();
        return OrderView.of(state.transit(no, OrderEvent.NO_SHOW, actor,
                "到访未遇：" + reason, req.version(), o -> {
                    o.setWorkerId(null);
                    o.setWorkerName(null);
                    o.setAssignedAt(null);
                }));
    }

    // ------------------------------------------------- 5 / 6 完工与登记缺件

    public OrderView finish(String no, TransitRequest req, Actor actor) {
        guard(no, OrderEvent.FINISH, actor);
        if (req.photosAfter() == null || req.photosAfter().isEmpty()) {
            throw BizException.precondition("完工照至少 1 张", "PHOTO_AFTER_REQUIRED");
        }
        return OrderView.of(state.transit(no, OrderEvent.FINISH, actor,
                isBlank(req.note()) ? "完工提交" : req.note(), req.version(), o -> {
                    o.setPhotosAfter(req.photosAfter());
                    o.setFinishAt(TimeUtil.now());
                }));
    }

    public OrderView needParts(String no, TransitRequest req, Actor actor) {
        guard(no, OrderEvent.NEED_PARTS, actor);
        if (isBlank(req.partsName())) {
            throw BizException.precondition("配件名称必填", "PARTS_NAME_REQUIRED");
        }
        BigDecimal amount = req.partsAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw BizException.precondition("金额要大于 0，管理端要按它判预算", "PARTS_AMOUNT_REQUIRED");
        }
        if (isBlank(req.partsEta())) {
            throw BizException.precondition("预计到货日期必填", "PARTS_ETA_REQUIRED");
        }
        return OrderView.of(state.transit(no, OrderEvent.NEED_PARTS, actor,
                "缺件：" + req.partsName() + " ¥" + amount, req.version(), o -> {
                    o.setPartsName(req.partsName());
                    o.setPartsAmount(amount);
                    o.setPartsEta(req.partsEta());
                }));
    }

    public OrderView partsArrived(String no, TransitRequest req, Actor actor) {
        return OrderView.of(state.transit(no, OrderEvent.PARTS_ARRIVED, actor,
                "配件到货，回队列重排（定级不降）", req.version(), o -> {
                    o.setPartsName(null);
                    o.setPartsAmount(null);
                    o.setPartsEta(null);
                }));
    }

    // ------------------------------------------------------------ 8 / 9 验收

    public OrderView accept(String no, TransitRequest req, Actor actor) {
        guard(no, OrderEvent.ACCEPT, actor);
        if (isBlank(req.signerName())) {
            throw BizException.precondition("签字人必填", "SIGNER_REQUIRED");
        }
        return OrderView.of(state.transit(no, OrderEvent.ACCEPT, actor,
                "验收通过，签字人 " + req.signerName(), req.version(),
                o -> o.setSignAt(TimeUtil.now())));
    }

    public OrderView reject(String no, TransitRequest req, Actor actor) {
        guard(no, OrderEvent.REJECT, actor);
        String reason = req.reason() == null ? "" : req.reason().trim();
        if (reason.length() < 2) {
            throw BizException.precondition("请写清哪里没修好，至少 2 个字", "REASON_REQUIRED");
        }
        // M1：原单转返修中。M2 接数据库时在这里同时新建 -R1 单（紧急度升一档），
        // 原单挂起、新单回待派工，SYS-004 第 9 条。
        return OrderView.of(state.transit(no, OrderEvent.REJECT, actor,
                "验收不通过：" + reason, req.version(), o -> o.setFinishAt(o.getFinishAt())));
    }

    // --------------------------------------------------------- 12 / 13 撤销

    public OrderView cancel(String no, TransitRequest req, Actor actor) {
        guard(no, OrderEvent.CANCEL, actor);
        WorkOrder cur = store.require(no);
        if (cur.getArriveAt() != null) {
            throw BizException.stateConflict("师傅已经进楼了，撤不了；要撤得走管理员那条（变更单 #001）",
                    cur.getStatus().name(), OrderEvent.CANCEL.name(),
                    StateMachine.allowedEvents(cur.getStatus()));
        }
        String reason = isBlank(req.reason()) ? "不需要修了" : req.reason();
        return OrderView.of(state.transit(no, OrderEvent.CANCEL, actor,
                "学生撤销：" + reason, req.version(), o -> o.setCancelledAt(TimeUtil.now())));
    }

    /** 维修中不许直接撤，只是留一条申请，等管理端决定（唯一由人决定的入口）。 */
    public OrderView cancelRequest(String no, TransitRequest req, Actor actor) {
        guard(no, OrderEvent.CANCEL_REQUEST, actor);
        if (store.require(no).getStatus() != OrderStatus.REPAIRING) {
            throw BizException.precondition(
                    "只有维修中的单才要申请撤销；待派工、已派工可以直接撤",
                    "CANCEL_REQUEST_ONLY_IN_REPAIRING");
        }
        String reason = isBlank(req.reason()) ? "学生申请撤销" : req.reason();
        return OrderView.of(state.logOnly(no, OrderEvent.CANCEL_REQUEST, actor,
                "申请撤销：" + reason, req.version()));
    }

    // ---------------------------------------------------------------- 查询

    public OrderView detail(String no) {
        return OrderView.of(store.require(no));
    }

    public List<OrderView> list(String status) {
        return store.all().stream()
                .filter(o -> {
                    if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
                        return true;
                    }
                    if ("ACTIVE".equalsIgnoreCase(status)) {
                        return o.getStatus().active();
                    }
                    if ("DONE".equalsIgnoreCase(status)) {
                        return !o.getStatus().active();
                    }
                    return o.getStatus() == OrderStatus.valueOf(status.toUpperCase());
                })
                .map(OrderView::of)
                .toList();
    }

    /** 先判身份、再判这条边在不在。顺序不许换：403 与 409 是两件事。 */
    private void guard(String no, OrderEvent event, Actor actor) {
        StateMachine.assertCanTrigger(event, actor.type());
        if (event.changesStatus()) {
            StateMachine.next(store.require(no).getStatus(), event);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

