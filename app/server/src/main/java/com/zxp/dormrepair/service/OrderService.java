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
import com.zxp.dormrepair.store.OrderRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 12 条命令。业务前置（照片、金额、归属）在这里判，状态怎么流转交给 {@link OrderStateService}。
 */
@Service
public class OrderService {

    private final OrderRepository store;
    private final OrderStateService state;
    private final AiClient ai;

    public OrderService(OrderRepository store, OrderStateService state, AiClient ai) {
        this.store = store;
        this.state = state;
        this.ai = ai;
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
        o.setDescription(desc);
        o.setVisitWindow(isBlank(req.window()) ? "全天（08:00-22:00）" : req.window());
        o.setPhotosBefore(req.photosBefore());
        // 定级：AI 说了算，AI 不在就兜底，绝不因为 AI 挂了拦住学生的单
        AiClient.Verdict v = isBlank(req.category())
                ? ai.classify(desc)
                : new AiClient.Verdict(req.category(), isBlank(req.level()) ? "普通" : req.level(),
                        1.0, "调用方指定类型，没走 AI", false, "MANUAL");
        o.setCategory(v.category());
        o.setLevel(v.level());
        o.setLevelSource(v.fallback() ? "FALLBACK" : v.source());
        o.setAiBasis(v.basis());
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

    /**
     * 楼栋级"进楼打卡"：师傅在楼门口按一次，背后生成 N 条单级 ARRIVE。
     *
     * <p>逐单独立事务 —— 一单失败不影响其他单（对应 C-12）。已经在修的进 skipped，
     * 不是本楼或不是 ASSIGNED 的既不记 ok 也不报 failed，免得界面上出现"打卡失败"的假警报。
     */
    public java.util.Map<String, Object> arriveBuilding(String buildingId, Actor actor) {
        java.util.List<String> ok = new java.util.ArrayList<>();
        java.util.List<String> skipped = new java.util.ArrayList<>();
        java.util.List<String> failed = new java.util.ArrayList<>();
        for (WorkOrder o : store.all()) {
            if (!buildingId.equals(o.getBuilding())) continue;
            if (o.getStatus() == OrderStatus.REPAIRING) { skipped.add(o.getNo()); continue; }
            if (o.getStatus() != OrderStatus.ASSIGNED) continue;
            if (!actor.id().equals(o.getWorkerId())) continue;
            try {
                arrive(o.getNo(), new TransitRequest(null, null, null, null, null, null, null, null, null, null), actor);
                ok.add(o.getNo());
            } catch (RuntimeException e) {
                failed.add(o.getNo());
            }
        }
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("buildingId", buildingId);
        m.put("arrivedAt", TimeUtil.now());
        m.put("ok", ok);
        m.put("skipped", skipped);
        m.put("failed", failed);
        return m;
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
        WorkOrder done = state.transit(no, OrderEvent.ACCEPT, actor,
                "验收通过，签字人 " + req.signerName(), req.version(),
                o -> o.setSignAt(TimeUtil.now()));
        // SYS-004 第 8 条：验收的是返修单，原单同时关闭（级联，不占 13 条转移）
        String base = done.getReworkOf();
        if (base != null) {
            WorkOrder origin = store.get(base);
            if (origin != null && origin.getStatus() == OrderStatus.REWORKING) {
                state.cascadeClose(base, actor,
                        "返修单 " + no + " 验收通过，原单关闭（级联，不占 13 条转移）");
            }
        }
        return OrderView.of(store.get(no));
    }

    public OrderView reject(String no, TransitRequest req, Actor actor) {
        guard(no, OrderEvent.REJECT, actor);
        String reason = req.reason() == null ? "" : req.reason().trim();
        if (reason.length() < 2) {
            throw BizException.precondition("请写清哪里没修好，至少 2 个字", "REASON_REQUIRED");
        }
        // 9 原单转返修中（挂起，等返修单走完）
        WorkOrder origin = state.transit(no, OrderEvent.REJECT, actor,
                "验收不通过：" + reason, req.version(), o -> { });
        WorkOrder rework = createRework(origin, reason, actor);
        return OrderView.of(store.get(rework.getNo()));
    }

    /**
     * 生成返修单（SYS-004 第 9 条）：新单号 原单号-R{n}，回待派工重新排队。
     *
     * <p>继承位置、报修人、类型与现场照片；紧急度升一档（普通→较急→紧急，到顶不再升）；
     * 原单的 reworkCount +1，累计到 2 次就进管理端"同一单反复返修"例外清单。
     */
    private WorkOrder createRework(WorkOrder origin, String reason, Actor actor) {
        String base = origin.getReworkOf() == null ? origin.getNo() : origin.getReworkOf();
        int max = 0;
        for (WorkOrder o : store.all()) {
            if (o.getNo().startsWith(base + "-R")) {
                String tail = o.getNo().substring((base + "-R").length());
                try {
                    max = Math.max(max, Integer.parseInt(tail));
                } catch (NumberFormatException ignored) {
                    // 单号不是数字就不算，防御性跳过
                }
            }
        }
        int idx = max + 1;
        String at = TimeUtil.now();

        WorkOrder r = new WorkOrder();
        r.setNo(base + "-R" + idx);
        r.setVersion(1);
        r.setStatus(StateMachine.onCreation(OrderEvent.SUBMIT));
        r.setBuilding(origin.getBuilding());
        r.setFloor(origin.getFloor());
        r.setRoom(origin.getRoom());
        r.setStudentId(origin.getStudentId());
        r.setStudentName(origin.getStudentName());
        r.setDescription(origin.getDescription());
        r.setVisitWindow(origin.getVisitWindow());
        r.setPhotosBefore(new java.util.ArrayList<>(origin.getPhotosBefore()));
        r.setCategory(origin.getCategory());
        String up = levelUp(origin.getLevel());
        r.setLevel(up);
        r.setLevelSource("REWORK");
        r.setAiBasis("返修单继承原单判定（" + origin.getLevel() + "→" + up + "）；第 " + idx
                + " 次返修，原因：" + reason);
        r.setCreatedAt(at);
        r.setStatusAt(at);
        r.setReworkOf(base);
        r.setReworkIndex(idx);
        r.setReworkCount(0);
        r.getEvents().add(new OrderEventLog(store.nextEventSeq(), at, OrderEvent.SUBMIT,
                actor.type().name(), actor.id(), actor.name(), 1,
                "返修单：由「" + origin.getNo() + "」验收不通过生成（" + reason + "）"));
        store.save(r);

        int count = (origin.getReworkCount() == null ? 0 : origin.getReworkCount()) + 1;
        store.update(base, origin.getVersion(), o -> o.setReworkCount(count));
        return r;
    }

    private static String levelUp(String level) {
        if ("普通".equals(level)) {
            return "较急";
        }
        return "紧急";
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

