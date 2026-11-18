package com.zxp.dormrepair.store;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zxp.dormrepair.common.BizException;
import com.zxp.dormrepair.common.TimeUtil;
import com.zxp.dormrepair.domain.OrderEventLog;
import com.zxp.dormrepair.domain.WorkOrder;
import com.zxp.dormrepair.statemachine.OrderEvent;
import com.zxp.dormrepair.store.entity.OrderEventRow;
import com.zxp.dormrepair.store.entity.OrderPhotoRow;
import com.zxp.dormrepair.store.mapper.OrderEventMapper;
import com.zxp.dormrepair.store.mapper.OrderPhotoMapper;
import com.zxp.dormrepair.store.mapper.SeqMapper;
import com.zxp.dormrepair.store.mapper.WorkOrderMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * MySQL 仓库（M2）。{@code app.store=mysql} 时生效。
 *
 * <p>三张表一起动：工单主表 + 事件表（只增）+ 照片表。改状态和写事件在同一个事务里，
 * 版本号由 {@code WHERE version = ?} 守住，写不进去就返回 false，上层抛 409。
 */
@Component
@ConditionalOnProperty(name = "app.store", havingValue = "mysql")
public class MybatisOrderRepository implements OrderRepository {

    private final WorkOrderMapper orders;
    private final OrderEventMapper events;
    private final OrderPhotoMapper photos;
    private final SeqMapper seq;

    public MybatisOrderRepository(WorkOrderMapper orders, OrderEventMapper events,
                                  OrderPhotoMapper photos, SeqMapper seq) {
        this.orders = orders;
        this.events = events;
        this.photos = photos;
        this.seq = seq;
    }

    @Override
    public WorkOrder get(String no) {
        WorkOrder o = orders.selectById(no);
        if (o == null) {
            return null;
        }
        fill(o);
        return o;
    }

    @Override
    public WorkOrder require(String no) {
        WorkOrder o = get(no);
        if (o == null) {
            throw BizException.notFound("没有这张工单：" + no);
        }
        return o;
    }

    @Override
    public List<WorkOrder> all() {
        List<WorkOrder> list = orders.selectList(
                Wrappers.<WorkOrder>query().orderByDesc("created_at").orderByDesc("no"));
        list.forEach(this::fill);
        return list;
    }

    @Override
    public Page page(String status, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(pageSize, 200));
        var query = Wrappers.<WorkOrder>query();
        applyStatusFilter(query, status);
        Long count = orders.selectCount(query);
        int total = count == null ? 0 : count.intValue();
        int from = Math.min(total, (safePage - 1) * safeSize);
        List<WorkOrder> items = from >= total ? List.of() : orders.selectList(query
                .orderByDesc("created_at").orderByDesc("no")
                .last("LIMIT " + safeSize + " OFFSET " + from));
        items.forEach(this::fill);
        return new Page(items, total, safePage, safeSize);
    }

    @Override
    public Page pageForActor(com.zxp.dormrepair.statemachine.ActorType actorType, String actorId,
                             String status, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(pageSize, 200));
        var query = Wrappers.<WorkOrder>query();
        if (actorType == com.zxp.dormrepair.statemachine.ActorType.STUDENT) {
            query.eq("student_id", actorId);
        } else if (actorType == com.zxp.dormrepair.statemachine.ActorType.WORKER) {
            query.eq("worker_id", actorId);
        }
        applyStatusFilter(query, status);
        Long count = orders.selectCount(query);
        int total = count == null ? 0 : count.intValue();
        int from = Math.min(total, (safePage - 1) * safeSize);
        List<WorkOrder> items = from >= total ? List.of() : orders.selectList(query
                .orderByDesc("created_at").orderByDesc("no")
                .last("LIMIT " + safeSize + " OFFSET " + from));
        items.forEach(this::fill);
        return new Page(items, total, safePage, safeSize);
    }

    private static void applyStatusFilter(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<WorkOrder> query,
                                          String status) {
        if ("ACTIVE".equalsIgnoreCase(status)) {
            query.in("status", "PENDING", "ASSIGNED", "REPAIRING", "PARTS", "VERIFYING", "REWORKING");
        } else if ("DONE".equalsIgnoreCase(status)) {
            query.in("status", "DONE", "CLOSED", "CANCELLED");
        } else if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            query.eq("status", status.toUpperCase());
        }
    }

    @Override
    public List<WorkOrder> findTimerCandidates() {
        List<WorkOrder> list = orders.selectList(Wrappers.<WorkOrder>query()
                .in("status", "VERIFYING", "DONE")
                .orderByAsc("created_at"));
        list.forEach(this::fill);
        return list;
    }

    @Override
    @Transactional
    public void save(WorkOrder o) {
        orders.insert(o);
        insertPhotos(o.getNo(), "BEFORE", o.getPhotosBefore(), o.getPhotoIdsBefore(), o.getStudentId());
        insertPhotos(o.getNo(), "AFTER", o.getPhotosAfter(), o.getPhotoIdsAfter(), o.getWorkerId());
        for (OrderEventLog log : o.getEvents()) {
            insertEvent(o.getNo(), log);
        }
    }

    @Override
    @Transactional
    public boolean update(String no, Integer expectVersion, Consumer<WorkOrder> mutator) {
        WorkOrder cur = orders.selectById(no);
        if (cur == null) {
            throw BizException.notFound("没有这张工单：" + no);
        }
        fill(cur);

        int base = cur.getVersion();
        if (expectVersion != null && expectVersion != base) {
            return false;
        }

        int evBefore = cur.getEvents().size();
        int beforeCount = cur.getPhotosBefore().size();
        int afterCount = cur.getPhotosAfter().size();
        int beforeIdCount = cur.getPhotoIdsBefore().size();
        int afterIdCount = cur.getPhotoIdsAfter().size();

        mutator.accept(cur);
        cur.setVersion(base + 1);
        if (cur.getStatusAt() == null) {
            cur.setStatusAt(TimeUtil.now());
        }

        if (orders.updateCas(cur, base) == 0) {
            return false;
        }

        List<OrderEventLog> logs = cur.getEvents();
        for (int i = evBefore; i < logs.size(); i++) {
            long assigned = insertEvent(no, logs.get(i));
            if (assigned > 0) {
                logs.set(i, copy(logs.get(i), assigned));
            }
        }
        List<String> newAfter = cur.getPhotosAfter().subList(afterCount, cur.getPhotosAfter().size());
        List<String> newAfterIds = cur.getPhotoIdsAfter().subList(afterIdCount, cur.getPhotoIdsAfter().size());
        insertPhotos(no, "AFTER", new ArrayList<>(newAfter), new ArrayList<>(newAfterIds), cur.getWorkerId());
        List<String> newBefore = cur.getPhotosBefore().subList(beforeCount, cur.getPhotosBefore().size());
        List<String> newBeforeIds = cur.getPhotoIdsBefore().subList(beforeIdCount, cur.getPhotoIdsBefore().size());
        insertPhotos(no, "BEFORE", new ArrayList<>(newBefore), new ArrayList<>(newBeforeIds), cur.getStudentId());
        return true;
    }

    @Override
    public long nextEventSeq() {
        // 序号由 MySQL 自增主键给，插完再回填
        return 0L;
    }

    @Override
    public void appendEvent(WorkOrder o, OrderEventLog log) {
        insertEvent(o.getNo(), log);
    }

    @Override
    @Transactional
    public String nextNo() {
        String day = LocalDate.now(TimeUtil.ZONE).toString().replace("-", "");
        for (int i = 0; i < 1000; i++) {
            seq.bump(day, "BX");
            Integer v = seq.lockCurrent(day, "BX");
            int n = (v == null ? 2 : v) - 1;
            String candidate = "BX" + day + String.format("%03d", n);
            if (orders.selectById(candidate) == null) {
                return candidate;
            }
        }
        throw new IllegalStateException("当天工单号已用尽");
    }

    @Override
    @Transactional
    public void clear() {
        events.wipe();
        photos.wipe();
        orders.wipe();
    }

    @Override
    public int size() {
        Long n = orders.selectCount(null);
        return n == null ? 0 : n.intValue();
    }

    // ------------------------------------------------------------------

    private void fill(WorkOrder o) {
        List<OrderPhotoRow> ph = photos.selectList(Wrappers.<OrderPhotoRow>query()
                .eq("order_no", o.getNo()).orderByAsc("id"));
        List<String> before = new ArrayList<>();
        List<String> after = new ArrayList<>();
        List<String> beforeIds = new ArrayList<>();
        List<String> afterIds = new ArrayList<>();
        for (OrderPhotoRow p : ph) {
            if ("AFTER".equals(p.getKind())) {
                after.add(p.getUrl());
                if (p.getFileId() != null) afterIds.add(p.getFileId());
            } else {
                before.add(p.getUrl());
                if (p.getFileId() != null) beforeIds.add(p.getFileId());
            }
        }
        o.setPhotosBefore(before);
        o.setPhotosAfter(after);
        o.setPhotoIdsBefore(beforeIds);
        o.setPhotoIdsAfter(afterIds);

        List<OrderEventRow> rows = events.selectList(Wrappers.<OrderEventRow>query()
                .eq("order_no", o.getNo()).orderByAsc("seq"));
        List<OrderEventLog> logs = new ArrayList<>();
        for (OrderEventRow r : rows) {
            logs.add(new OrderEventLog(
                    r.getSeq() == null ? 0L : r.getSeq(), r.getAt(),
                    OrderEvent.valueOf(r.getEvent()), r.getActorType(), r.getActorId(),
                    r.getActorName(), r.getManual() == null ? 1 : r.getManual(), r.getNote()));
        }
        logs.sort(Comparator.comparingLong(OrderEventLog::seq));
        o.setEvents(logs);
    }

    private long insertEvent(String no, OrderEventLog log) {
        OrderEventRow r = new OrderEventRow();
        r.setOrderNo(no);
        r.setAt(log.at());
        r.setEvent(log.event().name());
        r.setActorType(log.actorType());
        r.setActorId(log.actorId());
        r.setActorName(log.actorName());
        r.setManual(log.manual());
        r.setNote(log.note());
        events.insert(r);
        return r.getSeq() == null ? 0L : r.getSeq();
    }

    private void insertPhotos(String no, String kind, List<String> urls, List<String> fileIds, String by) {
        if (urls == null || urls.isEmpty()) {
            return;
        }
        String at = TimeUtil.now();
        for (int i = 0; i < urls.size(); i++) {
            OrderPhotoRow p = new OrderPhotoRow();
            p.setOrderNo(no);
            p.setKind(kind);
            p.setUrl(urls.get(i));
            p.setFileId(fileIds != null && i < fileIds.size() ? fileIds.get(i) : null);
            p.setUploadedBy(by == null ? "unknown" : by);
            p.setCreatedAt(at);
            photos.insert(p);
        }
    }

    private OrderEventLog copy(OrderEventLog log, long seq) {
        return new OrderEventLog(seq, log.at(), log.event(), log.actorType(), log.actorId(),
                log.actorName(), log.manual(), log.note());
    }
}
