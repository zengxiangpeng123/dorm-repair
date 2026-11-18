package com.zxp.dormrepair.service;

import com.zxp.dormrepair.common.TimeUtil;
import com.zxp.dormrepair.domain.Actor;
import com.zxp.dormrepair.domain.ExceptionRecord;
import com.zxp.dormrepair.domain.OrderEventLog;
import com.zxp.dormrepair.domain.ReworkReview;
import com.zxp.dormrepair.domain.WorkOrder;
import com.zxp.dormrepair.dto.OrderView;
import com.zxp.dormrepair.statemachine.OrderEvent;
import com.zxp.dormrepair.statemachine.OrderStatus;
import com.zxp.dormrepair.store.AiReviewTaskRepository;
import com.zxp.dormrepair.store.ExceptionRecordRepository;
import com.zxp.dormrepair.store.OrderRepository;
import com.zxp.dormrepair.store.ReworkReviewRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

/**
 * 只读查询。写操作一条都不在这里 —— 免得有人从查询里顺手改状态。
 *
 * <p>三条口径都写死在这里，接口与文档都引用它：
 * ① 师傅"要上楼"只算 PENDING/ASSIGNED/REPAIRING/PARTS，验收及以后不用上楼；
 * ② 例外清单只出四类，前三类不占状态位；
 * ③ 零人工完成率两套口径（严格：全部事件 manual=0；宽松：管理端一次都没碰过这张单）。
 */
@Service
public class QueryService {

    /** 要上楼的状态 */
    public static final Set<OrderStatus> WALK = Set.of(
            OrderStatus.PENDING, OrderStatus.ASSIGNED, OrderStatus.REPAIRING, OrderStatus.PARTS);

    /** 在办 */
    public static final Set<OrderStatus> ACTIVE = Set.of(
            OrderStatus.PENDING, OrderStatus.ASSIGNED, OrderStatus.REPAIRING,
            OrderStatus.PARTS, OrderStatus.VERIFYING, OrderStatus.REWORKING);

    private final OrderRepository store;
    private final ConfigService config;
    private final ReworkReviewRepository reviews;
    private final ExceptionRecordRepository exceptionRecords;
    private final AiReviewTaskRepository aiTasks;

    public QueryService(OrderRepository store, ConfigService config,
                        ReworkReviewRepository reviews,
                        ExceptionRecordRepository exceptionRecords,
                        AiReviewTaskRepository aiTasks) {
        this.store = store;
        this.config = config;
        this.reviews = reviews;
        this.exceptionRecords = exceptionRecords;
        this.aiTasks = aiTasks;
    }

    // ------------------------------------------------------------ 我的工单

    /** 学生看自己的、师傅看派给自己的、管理端看全部。 */
    public List<OrderView> myOrders(Actor actor, String status) {
        return store.all().stream()
                .filter(o -> switch (actor.type()) {
                    case STUDENT -> actor.id().equals(o.getStudentId());
                    case WORKER -> actor.id().equals(o.getWorkerId());
                    default -> true;
                })
                .filter(o -> match(status, o.getStatus()))
                .sorted(Comparator.comparing(WorkOrder::getCreatedAt).reversed())
                .map(OrderView::of)
                .toList();
    }

    private boolean match(String status, OrderStatus s) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) return true;
        if ("ACTIVE".equalsIgnoreCase(status)) return ACTIVE.contains(s);
        if ("DONE".equalsIgnoreCase(status)) return s == OrderStatus.DONE || s == OrderStatus.CLOSED;
        try {
            return s == OrderStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    // ------------------------------------------------------------ 师傅端

    /** 师傅今日待办：按楼栋分组，楼层从高到低（跑楼是自上而下，不是自下而上）。 */
    public Map<String, Object> workerTodo(Actor actor) {
        List<WorkOrder> mine = store.all().stream()
                .filter(o -> actor.id().equals(o.getWorkerId()))
                .toList();

        Map<String, List<WorkOrder>> byBuilding = new TreeMap<>();
        for (WorkOrder o : mine) byBuilding.computeIfAbsent(o.getBuilding(), k -> new ArrayList<>()).add(o);

        List<Map<String, Object>> buildings = new ArrayList<>();
        int todo = 0, repairing = 0, parts = 0;
        for (Map.Entry<String, List<WorkOrder>> e : byBuilding.entrySet()) {
            List<WorkOrder> list = e.getValue();
            List<OrderView> walk = list.stream().filter(o -> WALK.contains(o.getStatus()))
                    .sorted(Comparator.comparingInt(WorkOrder::getFloor).reversed()
                            .thenComparing(WorkOrder::getRoom))
                    .map(OrderView::of).toList();

            Map<Integer, Integer> floorCount = new LinkedHashMap<>();
            walk.forEach(v -> floorCount.merge(v.floor(), 1, Integer::sum));
            List<Map<String, Object>> floors = new ArrayList<>();
            new TreeMap<>(floorCount).descendingMap()
                    .forEach((f, c) -> floors.add(Map.of("floor", f, "count", c)));

            int done = (int) list.stream().filter(o -> o.getStatus() == OrderStatus.DONE
                    || o.getStatus() == OrderStatus.CLOSED).count();

            Map<String, Object> b = new LinkedHashMap<>();
            b.put("buildingId", e.getKey());
            b.put("buildingName", nameOf(e.getKey()));
            b.put("arrived", list.stream().anyMatch(o -> o.getArriveAt() != null));
            b.put("orders", walk);
            b.put("floors", floors);
            b.put("progress", Map.of("done", done, "total", list.size()));
            buildings.add(b);

            todo += (int) list.stream().filter(o -> o.getStatus() == OrderStatus.PENDING
                    || o.getStatus() == OrderStatus.ASSIGNED).count();
            repairing += (int) list.stream().filter(o -> o.getStatus() == OrderStatus.REPAIRING).count();
            parts += (int) list.stream().filter(o -> o.getStatus() == OrderStatus.PARTS).count();
        }
        return Map.of("buildings", buildings, "summary", Map.of(
                "todo", todo, "repairing", repairing, "parts", parts));
    }

    /** 本楼清单：楼层从高到低，空楼层单独列出来（省得师傅白跑一层）。 */
    public Map<String, Object> buildingBoard(String building, List<Integer> knownFloors) {
        List<WorkOrder> all = store.all().stream()
                .filter(o -> building.equals(o.getBuilding())).toList();

        Map<Integer, List<OrderView>> byFloor = new TreeMap<>(Comparator.reverseOrder());
        for (WorkOrder o : all) byFloor.computeIfAbsent(o.getFloor(), k -> new ArrayList<>()).add(OrderView.of(o));

        List<Map<String, Object>> floors = new ArrayList<>();
        byFloor.forEach((f, list) -> floors.add(Map.of("floor", f, "orders", list)));

        List<Integer> empty = knownFloors.stream().filter(f -> !byFloor.containsKey(f)).toList();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("buildingId", building);
        m.put("buildingName", nameOf(building));
        m.put("floors", floors);
        m.put("emptyFloors", empty);
        return m;
    }

    // ------------------------------------------------------------ 管理端

    /** 待管理员处理的返修申请。 */
    public Map<String, Object> reworkReviews() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (ReworkReview r : reviews.pending()) {
            WorkOrder o = store.get(r.orderNo());
            if (o == null) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.id());
            m.put("no", r.orderNo());
            m.put("attemptNo", r.attemptNo());
            m.put("status", r.status());
            m.put("requestedBy", r.requestedBy());
            m.put("requestedAt", r.requestedAt());
            m.put("reason", r.requestReason());
            m.put("reworkCount", o.getReworkCount() == null ? 0 : o.getReworkCount());
            m.put("building", o.getBuilding());
            m.put("floor", o.getFloor());
            m.put("room", o.getRoom());
            m.put("location", o.location());
            m.put("workerName", o.getWorkerName());
            m.put("version", o.getVersion());
            m.put("photosBefore", o.getPhotosBefore());
            m.put("photosAfter", o.getPhotosAfter());
            m.put("photoIdsBefore", o.getPhotoIdsBefore());
            m.put("photoIdsAfter", o.getPhotoIdsAfter());
            m.put("order", OrderView.of(o, r));
            items.add(m);
        }
        return Map.of("items", items, "total", items.size());
    }

    /** 管理端查看 AI 复核审计；只读、最多 100 条。 */
    public Map<String, Object> aiReviewTasks(int limit) {
        var items = aiTasks.recent(limit);
        return Map.of("items", items, "total", items.size());
    }

    /** 四类例外。前三类不动状态，第四类（学生申请撤销）才是唯一由人决定的入口。 */
    public Map<String, Object> exceptions() {
        int budget = config.budgetLine();
        List<Map<String, Object>> items = new ArrayList<>();

        for (WorkOrder o : store.all()) {
            // 1 数据对不上：已经推到待验收及以后，却一张完工照都没有
            if (o.getFinishAt() != null && (o.getPhotosAfter() == null || o.getPhotosAfter().isEmpty())
                    && o.getStatus() != OrderStatus.CANCELLED) {
                items.add(item("MISMATCH", "数据对不上", o,
                        "状态已经是「" + o.getStatus().label() + "」，但完工照 0 张（应 ≥1）"));
            }
            // 2 超预算采购：花过就留着，配件到货了也不消失，一直到归档
            if (o.getPartsAmount() != null && o.getPartsAmount().compareTo(BigDecimal.valueOf(budget)) > 0
                    && o.getStatus() != OrderStatus.CLOSED && o.getStatus() != OrderStatus.CANCELLED) {
                boolean arrived = o.getStatus() != OrderStatus.PARTS;
                items.add(item("OVER_BUDGET", "超预算采购", o,
                        "配件 " + o.getPartsName() + " ¥" + o.getPartsAmount().toPlainString()
                                + "，超过预算线 " + budget + " 元"
                                + (arrived ? "（配件已到、单已回队列，这条留到归档）" : "")));
            }
            // 3 学生申请撤销：进楼之后才有的，唯一需要人拍板的
            boolean asked = o.getEvents().stream().anyMatch(e -> e.event() == OrderEvent.CANCEL_REQUEST);
            if (asked && o.getStatus() == OrderStatus.REPAIRING) {
                items.add(item("CANCEL_REQUEST", "学生申请撤销", o,
                        "师傅已经进楼，学生申请撤销。是否取消工单及如何补偿师傅，由管理员决定"));
            }
        }
        // 3 反复返修：第 2 次批准时写入持久记录，不再由查询现场推算，保证事务内可追溯。
        for (ExceptionRecord r : exceptionRecords.openByKind("REPEAT_REWORK")) {
            WorkOrder o = store.get(r.orderNo());
            if (o != null) {
                items.add(item("REPEAT_REWORK", "反复返修", o,
                        r.detail() == null ? "同一原单已发生多次返修" : r.detail()));
            }
        }
        items.sort(Comparator.comparing((Map<String, Object> m) -> String.valueOf(m.get("kind")))
                .thenComparing(m -> String.valueOf(m.get("no"))));

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String k : List.of("MISMATCH", "OVER_BUDGET", "REPEAT_REWORK", "CANCEL_REQUEST")) {
            counts.put(k, (int) items.stream().filter(m -> k.equals(m.get("kind"))).count());
        }
        return Map.of("items", items, "counts", counts, "total", items.size());
    }

    private Map<String, Object> item(String kind, String kindLabel, WorkOrder o, String detail) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", "EX-" + kind + "-" + o.getNo());
        m.put("kind", kind);
        m.put("kindLabel", kindLabel);
        m.put("no", o.getNo());
        m.put("building", o.getBuilding());
        m.put("room", o.getRoom());
        m.put("floor", o.getFloor());
        m.put("location", o.getBuilding() + " " + o.getFloor() + "F-" + o.getRoom());
        m.put("status", o.getStatus().name());
        m.put("statusLabel", o.getStatus().label());
        m.put("detail", detail);
        m.put("version", o.getVersion());
        return m;
    }

    /** 看板。零人工两套口径都算，并在 basis 里写清怎么算的 —— 免得评审时被问住。 */
    public Map<String, Object> dashboard() {
        List<WorkOrder> all = store.all();
        List<WorkOrder> done = all.stream()
                .filter(o -> o.getStatus() == OrderStatus.DONE || o.getStatus() == OrderStatus.CLOSED)
                .toList();

        int strict = (int) done.stream().filter(this::allManualZero).count();
        int loose = (int) done.stream().filter(this::noAdminTouch).count();

        long fixSum = 0; int fixN = 0;
        for (WorkOrder o : all) {
            Long a = TimeUtil.epochMs(o.getArriveAt());
            Long f = TimeUtil.epochMs(o.getFinishAt());
            if (a != null && f != null && f > a) { fixSum += f - a; fixN++; }
        }
        long reworkN = all.stream().filter(o -> o.getEvents().stream()
                .anyMatch(e -> e.event() == OrderEvent.REJECT)).count();

        List<Map<String, Object>> byBuilding = new ArrayList<>();
        for (String b : List.of("B7", "B8")) {
            List<WorkOrder> list = all.stream().filter(o -> b.equals(o.getBuilding())).toList();
            if (list.isEmpty()) continue;
            List<WorkOrder> bd = list.stream().filter(o -> o.getStatus() == OrderStatus.DONE
                    || o.getStatus() == OrderStatus.CLOSED).toList();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("buildingId", b);
            m.put("buildingName", nameOf(b));
            m.put("active", list.stream().filter(o -> ACTIVE.contains(o.getStatus())).count());
            m.put("done", bd.size());
            byBuilding.add(m);
        }
        // 楼栋例外数单独算一遍，别把 exceptions() 里的整表拼进去
        Map<String, Integer> exByBuilding = new LinkedHashMap<>();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> exItems = (List<Map<String, Object>>) exceptions().get("items");
        for (Map<String, Object> e : exItems) {
            exByBuilding.merge(String.valueOf(e.get("building")), 1, Integer::sum);
        }
        byBuilding.forEach(m -> m.put("exceptions", exByBuilding.getOrDefault(String.valueOf(m.get("buildingId")), 0)));

        List<Map<String, Object>> timers = all.stream()
                .filter(o -> o.getStatus() == OrderStatus.VERIFYING || o.getStatus() == OrderStatus.DONE)
                .map(o -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("no", o.getNo());
                    m.put("room", o.getRoom());
                    m.put("building", o.getBuilding());
                    m.put("status", o.getStatus().name());
                    m.putAll(timers(o));
                    return m;
                }).toList();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("active", all.stream().filter(o -> ACTIVE.contains(o.getStatus())).count());
        m.put("pendingArchive", all.stream().filter(o -> o.getStatus() == OrderStatus.DONE).count());
        m.put("exceptionTotal", exItems.size());
        m.put("avgFixMinutes", fixN == 0 ? 0 : Math.round((double) fixSum / fixN / 60000));
        m.put("reworkRate", all.isEmpty() ? 0 : Math.round(reworkN * 100.0 / all.size()) / 100.0);
        m.put("zeroManual", Map.of(
                "zero", loose, "total", done.size(),
                "rate", done.isEmpty() ? 0 : Math.round(loose * 100.0 / done.size()) / 100.0,
                "basis", "宽松口径：管理端一次都没碰过这张单（没有管理员事件、派工也不是人工）"));
        m.put("strictZero", Map.of(
                "zero", strict, "total", done.size(),
                "rate", done.isEmpty() ? 0 : Math.round(strict * 100.0 / done.size()) / 100.0,
                "basis", "严格口径：这张单所有事件 manual=0（学生提交、师傅打卡都算 manual=1，所以通常恒为 0）"));
        m.put("byBuilding", byBuilding);
        m.put("timers", timers);
        m.put("config", config.all());
        return m;
    }

    private boolean allManualZero(WorkOrder o) {
        return o.getEvents().stream().allMatch(e -> e.manual() == 0);
    }

    private boolean noAdminTouch(WorkOrder o) {
        for (OrderEventLog e : o.getEvents()) {
            if ("ADMIN".equalsIgnoreCase(e.actorType())) return false;
            if (e.event() == OrderEvent.ASSIGN && !"SYSTEM".equalsIgnoreCase(e.actorType())) return false;
        }
        return true;
    }

    // ------------------------------------------------------------ 计时器

    /**
     * 两颗表各算各的：T1 从完工（finishAt）起算 168 小时，T2 从签字（signAt）起算 7 天。
     * 同一张单不会同时有两颗 —— 待验收时只有 T1，已完成时只有 T2。
     */
    public Map<String, Object> timers(WorkOrder o) {
        long now = TimeUtil.nowMs();
        Map<String, Object> m = new LinkedHashMap<>();
        long t1ms = config.verifyHours() * 3600_000L;
        long t2ms = config.archiveDays() * 86_400_000L;

        Long finish = TimeUtil.epochMs(o.getFinishAt());
        if (finish != null && o.getStatus() == OrderStatus.VERIFYING) {
            long deadline = finish + t1ms;
            m.put("t1", Map.of("from", TimeUtil.fmt(finish), "deadline", TimeUtil.fmt(deadline),
                    "leftMs", deadline - now, "total", t1ms, "label", "验收倒计时"));
        }
        Long sign = TimeUtil.epochMs(o.getSignAt());
        if (sign != null && o.getStatus() == OrderStatus.DONE) {
            long deadline = sign + t2ms;
            m.put("t2", Map.of("from", TimeUtil.fmt(sign), "deadline", TimeUtil.fmt(deadline),
                    "leftMs", deadline - now, "total", t2ms, "label", "归档倒计时"));
        }
        Long created = TimeUtil.epochMs(o.getCreatedAt());
        m.put("stayedMs", created == null ? 0L : now - created);
        return m;
    }

    public String nameOf(String building) {
        return switch (building) {
            case "B7" -> "东区7号楼";
            case "B8" -> "东区8号楼";
            default -> building;
        };
    }
}
