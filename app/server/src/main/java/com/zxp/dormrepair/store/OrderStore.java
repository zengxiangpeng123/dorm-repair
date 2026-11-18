package com.zxp.dormrepair.store;

import com.zxp.dormrepair.common.BizException;
import com.zxp.dormrepair.common.TimeUtil;
import com.zxp.dormrepair.domain.OrderEventLog;
import com.zxp.dormrepair.domain.WorkOrder;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/**
 * M1 的内存仓库。M2 换成 MyBatis-Plus 之后，外面的 service 一行都不用改。
 *
 * <p>并发只做两件事：每张单一把锁（同一张单串行）+ version 比对（旧版本写不进去）。
 */
@Component
public class OrderStore {

    private final Map<String, WorkOrder> byNo = new ConcurrentHashMap<>();
    private final AtomicLong eventSeq = new AtomicLong();

    /** 取一份拷贝，读的时候不怕别人正在改。 */
    public WorkOrder get(String no) {
        WorkOrder o = byNo.get(no);
        if (o == null) {
            return null;
        }
        synchronized (o) {
            return copy(o);
        }
    }

    public WorkOrder require(String no) {
        WorkOrder o = get(no);
        if (o == null) {
            throw BizException.notFound("没有这张工单：" + no);
        }
        return o;
    }

    public List<WorkOrder> all() {
        List<WorkOrder> out = new ArrayList<>();
        for (WorkOrder o : byNo.values()) {
            synchronized (o) {
                out.add(copy(o));
            }
        }
        out.sort(Comparator.comparing(WorkOrder::getCreatedAt).reversed());
        return out;
    }

    public void save(WorkOrder o) {
        byNo.put(o.getNo(), o);
    }

    /**
     * 带版本号的一次改写。expectVersion 为 null 表示不校验（内部系统事件）。
     *
     * @return false 表示版本对不上，调用方要抛 409
     */
    public boolean update(String no, Integer expectVersion, Consumer<WorkOrder> mutator) {
        WorkOrder o = byNo.get(no);
        if (o == null) {
            throw BizException.notFound("没有这张工单：" + no);
        }
        synchronized (o) {
            if (expectVersion != null && expectVersion != o.getVersion()) {
                return false;
            }
            mutator.accept(o);
            o.setVersion(o.getVersion() + 1);
            o.setStatusAt(TimeUtil.now());
            return true;
        }
    }

    public long nextEventSeq() {
        return eventSeq.incrementAndGet();
    }

    public void appendEvent(WorkOrder o, OrderEventLog log) {
        o.getEvents().add(log);
    }

    /** 单号：BX + 当天日期 8 位 + 3 位序号。 */
    public String nextNo() {
        String day = LocalDate.now(TimeUtil.ZONE).toString().replace("-", "");
        String prefix = "BX" + day;
        int max = 0;
        for (String no : byNo.keySet()) {
            if (no.startsWith(prefix) && no.length() == prefix.length() + 3) {
                max = Math.max(max, Integer.parseInt(no.substring(prefix.length())));
            }
        }
        return prefix + String.format("%03d", max + 1);
    }

    public void clear() {
        byNo.clear();
        eventSeq.set(0);
    }

    public int size() {
        return byNo.size();
    }

    private WorkOrder copy(WorkOrder o) {
        WorkOrder c = new WorkOrder();
        c.setNo(o.getNo());
        c.setStatus(o.getStatus());
        c.setVersion(o.getVersion());
        c.setBuilding(o.getBuilding());
        c.setFloor(o.getFloor());
        c.setRoom(o.getRoom());
        c.setStudentId(o.getStudentId());
        c.setStudentName(o.getStudentName());
        c.setDesc(o.getDesc());
        c.setWindow(o.getWindow());
        c.setPhotosBefore(new ArrayList<>(o.getPhotosBefore()));
        c.setPhotosAfter(new ArrayList<>(o.getPhotosAfter()));
        c.setCategory(o.getCategory());
        c.setLevel(o.getLevel());
        c.setLevelSource(o.getLevelSource());
        c.setAiBasis(o.getAiBasis());
        c.setWorkerId(o.getWorkerId());
        c.setWorkerName(o.getWorkerName());
        c.setPartsName(o.getPartsName());
        c.setPartsAmount(o.getPartsAmount());
        c.setPartsEta(o.getPartsEta());
        c.setCreatedAt(o.getCreatedAt());
        c.setAssignedAt(o.getAssignedAt());
        c.setArriveAt(o.getArriveAt());
        c.setFinishAt(o.getFinishAt());
        c.setSignAt(o.getSignAt());
        c.setClosedAt(o.getClosedAt());
        c.setCancelledAt(o.getCancelledAt());
        c.setStatusAt(o.getStatusAt());
        c.setEvents(new ArrayList<>(o.getEvents()));
        return c;
    }
}
