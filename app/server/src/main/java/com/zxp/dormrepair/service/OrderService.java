package com.zxp.dormrepair.service;

import com.zxp.dormrepair.auth.UserAccount;
import com.zxp.dormrepair.auth.UserRepository;
import com.zxp.dormrepair.common.BizException;
import com.zxp.dormrepair.common.TimeUtil;
import com.zxp.dormrepair.domain.Actor;
import com.zxp.dormrepair.domain.OrderEventLog;
import com.zxp.dormrepair.domain.ReworkReview;
import com.zxp.dormrepair.domain.WorkOrder;
import com.zxp.dormrepair.dto.OrderView;
import com.zxp.dormrepair.dto.ReworkRequestCreate;
import com.zxp.dormrepair.dto.ReworkReviewRequest;
import com.zxp.dormrepair.dto.SubmitRequest;
import com.zxp.dormrepair.dto.TransitRequest;
import com.zxp.dormrepair.statemachine.ActorType;
import com.zxp.dormrepair.statemachine.OrderEvent;
import com.zxp.dormrepair.statemachine.OrderStatus;
import com.zxp.dormrepair.statemachine.StateMachine;
import com.zxp.dormrepair.store.ExceptionRecordRepository;
import com.zxp.dormrepair.store.OrderRepository;
import com.zxp.dormrepair.store.ReworkReviewRepository;
import com.zxp.dormrepair.file.FileRecord;
import com.zxp.dormrepair.file.FileRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 12 条命令。业务前置（照片、金额、归属）在这里判，状态怎么流转交给 {@link OrderStateService}。
 */
@Service
public class OrderService {

    private final OrderRepository store;
    private final OrderStateService state;
    private final AiClient ai;
    private final AiReviewService review;
    private final ConfigService config;
    private final ReworkReviewRepository reviews;
    private final UserRepository users;
    private final ExceptionRecordRepository exceptions;
    private final FileRepository files;
    private final boolean autoAssign;
    private final boolean legacyDemoPhotos;

    public OrderService(OrderRepository store, OrderStateService state, AiClient ai,
                        AiReviewService review, ConfigService config,
                        ReworkReviewRepository reviews, UserRepository users,
                        ExceptionRecordRepository exceptions, FileRepository files,
                        @org.springframework.beans.factory.annotation.Value("${app.order.auto-assign:true}") boolean autoAssign,
                        @org.springframework.beans.factory.annotation.Value("${app.file.allow-legacy-demo-urls:false}") boolean legacyDemoPhotos) {
        this.store = store;
        this.state = state;
        this.ai = ai;
        this.review = review;
        this.config = config;
        this.reviews = reviews;
        this.users = users;
        this.exceptions = exceptions;
        this.files = files;
        this.autoAssign = autoAssign;
        this.legacyDemoPhotos = legacyDemoPhotos;
    }

    // ---------------------------------------------------------------- 1 建单

    public OrderView submit(SubmitRequest req, Actor actor) {
        StateMachine.assertCanTrigger(OrderEvent.SUBMIT, actor.type());
        List<String> beforePhotoIds = bindPhotos(req.photoIdsBefore(), req.photosBefore(), "BEFORE", actor);
        if (beforePhotoIds.isEmpty() && (req.photosBefore() == null || req.photosBefore().isEmpty())) {
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
        o.setPhotoIdsBefore(beforePhotoIds);
        o.setPhotosBefore(displayUrls(beforePhotoIds, req.photosBefore()));
        // 定级分两步：提交这一刻只跑规则（毫秒级，学生不用等模型）；
        // 模型在后台复核，回来把更准的类型和紧急程度写回去。三步都不碰工单状态。
        AiClient.Verdict v = isBlank(req.category())
                ? ai.classifyFast(desc)
                : AiClient.Verdict.manual(req.category(), isBlank(req.level()) ? "普通" : req.level(),
                        "调用方指定类型，没走 AI");
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
        beforePhotoIds.forEach(id -> files.bind(id, o.getNo(), "BEFORE"));
        if (autoAssign) {
            // 派工是系统动作，由后端在提交事务内完成；学生端不再调用 /assign。
            assign(o.getNo(), new TransitRequest(null, null, null, null, null, null,
                    null, null, null, null, null), Actor.system());
        }
        WorkOrder created = store.require(o.getNo());
        if (isBlank(req.category())) {
            // 任务版本必须取自所有建单内联动作完成后的最终快照；否则自动派工后的
            // 版本变化会被误判成晚到结果，或让版本守卫失去意义。
            review.review(created.getNo(), created.getVersion(), desc);   // 异步，立刻返回
        }
        return OrderView.of(created);
    }

    // ------------------------------------------------------- 2 / 3 派工与到门

    public OrderView assign(String no, TransitRequest req, Actor actor) {
        WorkOrder cur = store.require(no);
        UserAccount worker;
        if (isBlank(req.workerId())) {
            worker = users.findWorkerForBuilding(cur.getBuilding())
                    .orElseThrow(() -> BizException.precondition(
                            "本楼栋当前没有可派师傅", "NO_WORKER_AVAILABLE"));
        } else {
            worker = users.find(req.workerId())
                    .filter(u -> u.role() == ActorType.WORKER && u.active())
                    .orElseThrow(() -> BizException.precondition(
                            "师傅不存在或已停用：" + req.workerId(), "WORKER_NOT_AVAILABLE"));
        }
        String workerName = isBlank(req.workerName()) ? worker.name() : req.workerName();
        String workerId = worker.id();
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
                arrive(o.getNo(), new TransitRequest(null, null, null, null, null, null, null, null, null, null, null), actor);
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
        List<String> afterPhotoIds = bindPhotos(req.photoIdsAfter(), req.photosAfter(), "AFTER", actor);
        if (afterPhotoIds.isEmpty() && (req.photosAfter() == null || req.photosAfter().isEmpty())) {
            throw BizException.precondition("完工照至少 1 张", "PHOTO_AFTER_REQUIRED");
        }
        String at = TimeUtil.now();
        WorkOrder updated = state.transit(no, OrderEvent.FINISH, actor,
                isBlank(req.note()) ? "完工提交" : req.note(), req.version(), o -> {
                    o.setPhotoIdsAfter(afterPhotoIds);
                    o.setPhotosAfter(displayUrls(afterPhotoIds, req.photosAfter()));
                    o.setFinishAt(at);
                    o.setVerifyDueAt(plusHours(at, config.verifyHours()));
                });
        afterPhotoIds.forEach(id -> files.bind(id, no, "AFTER"));
        return OrderView.of(updated);
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

    @Transactional
    public OrderView accept(String no, TransitRequest req, Actor actor) {
        guard(no, OrderEvent.ACCEPT, actor);
        if (isBlank(req.signerName())) {
            throw BizException.precondition("签字人必填", "SIGNER_REQUIRED");
        }
        String at = TimeUtil.now();
        WorkOrder done = state.transit(no, OrderEvent.ACCEPT, actor,
                "验收通过，签字人 " + req.signerName(), req.version(), o -> {
                    o.setSignAt(at);
                    o.setArchiveDueAt(plusDays(at, config.archiveDays()));
                });
        // SYS-004 第 8 条：验收的是返修单，原单同时关闭（级联，不占 14 条转移）
        String base = done.getReworkOf();
        if (base != null) {
            // 第一次返修只有原单；R2 以后，上一张返修单也停在 REWORKING。
            // 最终验收通过时要关闭整个返修链，不能让中间单永久挂在办。
            List<WorkOrder> waitingParents = store.all().stream()
                    .filter(o -> o.getNo().equals(base) || base.equals(o.getReworkOf()))
                    .filter(o -> o.getStatus() == OrderStatus.REWORKING)
                    .toList();
            for (WorkOrder parent : waitingParents) {
                state.cascadeClose(parent.getNo(), actor,
                        "返修单 " + no + " 验收通过，前序工单关闭（级联，不占 14 条转移）");
            }
        }
        return OrderView.of(store.get(no));
    }

    @Transactional
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

    /** 学生提交返修申请：不改变状态，只写事件与审查行。 */
    @Transactional
    public Map<String, Object> requestRework(String no, ReworkRequestCreate req, Actor actor) {
        StateMachine.assertCanTrigger(OrderEvent.REWORK_REQUEST, actor.type());
        WorkOrder cur = store.require(no);
        if (!actor.id().equals(cur.getStudentId())) {
            throw BizException.notOwner("这张单不是你的：" + no);
        }
        if (cur.getStatus() != OrderStatus.VERIFYING) {
            throw BizException.precondition("只有待验收的工单可以申请返修", "REWORK_ONLY_VERIFYING");
        }
        if (reviews.hasPending(no)) {
            throw BizException.precondition("这张单已经在返修审查中", "REWORK_REVIEW_PENDING");
        }
        if (req == null || req.version() == null) {
            throw BizException.precondition("返修申请必须带 version", "VERSION_REQUIRED");
        }
        String reason = req.reason() == null ? "" : req.reason().trim();
        if (reason.length() < 2) {
            throw BizException.precondition("请写清哪里没修好，至少 2 个字", "REASON_REQUIRED");
        }
        String at = TimeUtil.now();
        WorkOrder updated = state.logOnly(no, OrderEvent.REWORK_REQUEST, actor,
                "申请返修：" + reason, req.version());
        ReworkReview review = reviews.createRequest(no, actor.id(), reason, at);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("order", OrderView.of(updated, review));
        out.put("review", OrderView.of(updated, review).reworkReview());
        return out;
    }

    /** 管理员审查返修申请；批准后才生成返修子单。 */
    @Transactional
    public Map<String, Object> reviewRework(String no, ReworkReviewRequest req, Actor admin) {
        StateMachine.assertCanTrigger(OrderEvent.REJECT, admin.type());
        WorkOrder cur = store.require(no);
        if (cur.getStatus() != OrderStatus.VERIFYING) {
            throw BizException.precondition("只有待验收的工单可以审查返修", "REVIEW_ONLY_VERIFYING");
        }
        ReworkReview pending = reviews.findPending(no)
                .orElseThrow(() -> BizException.precondition("没有待处理的返修申请", "REWORK_REVIEW_NOT_FOUND"));
        if (req == null || req.version() == null) {
            throw BizException.precondition("返修审查必须带 version", "VERSION_REQUIRED");
        }
        String decision = req.decision() == null ? "" : req.decision().trim().toUpperCase();
        String reason = req == null || req.reason() == null ? "" : req.reason().trim();
        if (reason.length() < 2) {
            throw BizException.precondition("请写清审查意见，至少 2 个字", "REVIEW_REASON_REQUIRED");
        }
        String at = TimeUtil.now();
        if ("APPROVE".equals(decision)) {
            WorkOrder origin = state.transit(no, OrderEvent.REJECT, admin,
                    "返修审查通过：" + reason, req.version(), o -> {
                        o.setVerifyDueAt(null);
                    });
            Actor student = Actor.of("STUDENT", origin.getStudentId(), origin.getStudentName());
            WorkOrder child = createRework(origin, pending.requestReason(), student);
            reviews.decide(pending, "APPROVED", child.getReworkIndex(), admin.id(), reason, at);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("original", OrderView.of(store.get(no), reviews.findLatest(no).orElse(null)));
            out.put("reworkOrder", OrderView.of(child));
            return out;
        }
        if ("REJECT".equals(decision)) {
            WorkOrder original = state.logOnly(no, OrderEvent.REWORK_REJECTED, admin,
                    "驳回返修申请：" + reason, req.version(), o -> {
                        o.setVerifyDueAt(plusHours(at, config.verifyHours()));
                    });
            ReworkReview rejected = reviews.decide(pending, "REJECTED", null, admin.id(), reason, at);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("original", OrderView.of(original, rejected));
            out.put("reworkOrder", null);
            return out;
        }
        throw BizException.badRequest("decision 只能是 APPROVE 或 REJECT");
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
        r.setPhotoIdsBefore(new java.util.ArrayList<>(origin.getPhotoIdsBefore()));
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

        WorkOrder root = store.require(base);
        int count = (root.getReworkCount() == null ? 0 : root.getReworkCount()) + 1;
        store.update(base, root.getVersion(), o -> o.setReworkCount(count));
        if (idx >= 2 && exceptions.findOpen("REPEAT_REWORK", base).isEmpty()) {
            exceptions.createRepeatRework(base,
                    "这张原单已经返修 " + count + " 次，该看是不是根因没找对", at);
        }
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

    /** 管理员处理维修中的撤销申请。仍走唯一 CANCEL 边，不新增状态；原因必须留痕。 */
    @Transactional
    public OrderView adminCancel(String no, TransitRequest req, Actor admin) {
        WorkOrder cur = store.require(no);
        if (cur.getStatus() != OrderStatus.REPAIRING) {
            throw BizException.precondition("只有维修中的工单可以处理撤销申请",
                    "ADMIN_CANCEL_ONLY_REPAIRING");
        }
        boolean requested = cur.getEvents().stream()
                .anyMatch(e -> e.event() == OrderEvent.CANCEL_REQUEST);
        if (!requested) {
            throw BizException.precondition("这张工单没有学生撤销申请", "CANCEL_REQUEST_NOT_FOUND");
        }
        if (req == null || req.version() == null) {
            throw BizException.precondition("撤销决定必须带 version", "VERSION_REQUIRED");
        }
        String reason = isBlank(req.reason()) ? "" : req.reason().trim();
        if (reason.length() < 2) {
            throw BizException.precondition("请写清管理员撤销原因，至少 2 个字", "CANCEL_REASON_REQUIRED");
        }
        return OrderView.of(state.transit(no, OrderEvent.ADMIN_CANCEL, admin,
                "管理员批准撤销：" + reason, req.version(), o -> o.setCancelledAt(TimeUtil.now())));
    }

    // ---------------------------------------------------------------- 查询

    public OrderView detail(String no) {
        WorkOrder o = store.require(no);
        return OrderView.of(o, reviews.findLatest(no).orElse(null));
    }

    /** 详情和事件流都只给本人、本单师傅或管理端看。 */
    public OrderView detailForActor(String no, Actor actor) {
        WorkOrder o = store.require(no);
        boolean visible = switch (actor.type()) {
            case ADMIN, SYSTEM -> true;
            case STUDENT -> actor.id().equals(o.getStudentId());
            case WORKER -> actor.id().equals(o.getWorkerId());
        };
        if (!visible) {
            throw BizException.forbidden("不能查看不属于你的工单：" + no);
        }
        return OrderView.of(o, reviews.findLatest(no).orElse(null));
    }

    /** 上传后的文件 ID 才能绑定到工单；旧 demo URL 只在显式 demo 模式兼容。 */
    private List<String> bindPhotos(List<String> ids, List<String> legacyUrls, String kind, Actor actor) {
        if (ids != null && !ids.isEmpty()) {
            if (ids.size() > 3) {
                throw BizException.precondition("每类照片最多 3 张", "PHOTO_LIMIT_EXCEEDED");
            }
            List<FileRecord> records = files.findByIds(ids);
            if (records.size() != ids.size()) {
                throw BizException.precondition("有照片不存在或已被清理，请重新上传", "PHOTO_NOT_FOUND");
            }
            for (FileRecord f : records) {
                if (!actor.id().equals(f.uploadedBy())) {
                    throw BizException.forbidden("不能绑定别人上传的照片");
                }
                if (!kind.equals(f.purpose())) {
                    throw BizException.precondition("照片用途不匹配", "PHOTO_PURPOSE_MISMATCH");
                }
                if (f.orderNo() != null) {
                    throw BizException.precondition("照片已经绑定到工单", "FILE_ALREADY_BOUND");
                }
            }
            return ids;
        }
        if (legacyUrls == null || legacyUrls.isEmpty()) {
            return List.of();
        }
        if (!legacyDemoPhotos) {
            throw BizException.precondition("生产环境必须使用上传接口返回的文件 ID", "PHOTO_UPLOAD_REQUIRED");
        }
        if (legacyUrls.size() > 3) {
            throw BizException.precondition("每类照片最多 3 张", "PHOTO_LIMIT_EXCEEDED");
        }
        if (legacyUrls.stream().anyMatch(u -> u == null || !u.startsWith("demo://"))) {
            throw BizException.precondition("演示环境只兼容 demo:// 旧数据", "PHOTO_UPLOAD_REQUIRED");
        }
        // 旧演示数据没有文件 ID；保留 URL 列表，并让调用方继续执行原有照片校验。
        return List.of();
    }

    private static List<String> displayUrls(List<String> ids, List<String> legacyUrls) {
        if (ids != null && !ids.isEmpty()) {
            return ids.stream().map(id -> "/api/v1/files/" + id).toList();
        }
        return legacyUrls == null ? List.of() : legacyUrls;
    }

    private static String plusHours(String at, int hours) {
        Long ms = TimeUtil.epochMs(at);
        return ms == null ? at : TimeUtil.fmt(ms + hours * 3600_000L);
    }

    private static String plusDays(String at, int days) {
        Long ms = TimeUtil.epochMs(at);
        return ms == null ? at : TimeUtil.fmt(ms + days * 24 * 3600_000L);
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

