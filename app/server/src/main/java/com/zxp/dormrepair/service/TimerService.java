package com.zxp.dormrepair.service;

import com.zxp.dormrepair.common.TimeUtil;
import com.zxp.dormrepair.domain.Actor;
import com.zxp.dormrepair.domain.WorkOrder;
import com.zxp.dormrepair.statemachine.OrderEvent;
import com.zxp.dormrepair.statemachine.OrderStatus;
import com.zxp.dormrepair.statemachine.StateMachine;
import com.zxp.dormrepair.store.OrderRepository;
import com.zxp.dormrepair.store.ReworkReviewRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 两颗计时器到点之后该发生的事。
 *
 * <p>T1：待验收超过 verifyHours 小时 → 系统自动验收（AUTO_ACCEPT，manual=0）。
 * <p>T2：已完成满 archiveDays 天 → 归档（ARCHIVE，转只读）。
 *
 * <p>这里只做"到点了就推事件"，一次一单、失败不影响其他单。真正的时限判断靠 finishAt / signAt，
 * 不靠扫描顺序 —— 所以快进和自然等待得到的结果一样。
 */
@Service
public class TimerService {

    private static final Logger log = LoggerFactory.getLogger(TimerService.class);
    private static final Actor SYSTEM = Actor.of("system", "system", "定时任务");

    private final OrderRepository store;
    private final OrderStateService state;
    private final ConfigService config;
    private final ReworkReviewRepository reviews;

    public TimerService(OrderRepository store, OrderStateService state, ConfigService config,
                        ReworkReviewRepository reviews) {
        this.store = store;
        this.state = state;
        this.config = config;
        this.reviews = reviews;
    }

    /** 30 秒扫一次。演示环境里也可以手动调 /demo/advance 立刻跑一遍。 */
    @Scheduled(fixedDelay = 30000L, initialDelay = 15000L)
    public void tick() {
        try {
            sweep(TimeUtil.now());
        } catch (RuntimeException e) {
            log.warn("定时扫描失败：{}", e.getMessage());
        }
    }

    /** 按给定时刻判断到点没有。virtualNow 传未来时间就等于"快进"。 */
    public Map<String, Object> sweep(String virtualNow) {
        Long nowMs = TimeUtil.epochMs(virtualNow);
        if (nowMs == null) {
            nowMs = TimeUtil.nowMs();
        }
        long t1Ms = config.verifyHours() * 3600_000L;
        long t2Ms = config.archiveDays() * 24 * 3600_000L;

        List<String> accepted = new ArrayList<>();
        List<String> archived = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        for (WorkOrder o : store.findTimerCandidates()) {
            if (o.getStatus() == OrderStatus.VERIFYING) {
                if (reviews.hasPending(o.getNo())) {
                    continue;
                }
                Long due = TimeUtil.epochMs(o.getVerifyDueAt());
                if (due == null) {
                    Long f = TimeUtil.epochMs(o.getFinishAt());
                    due = f == null ? null : f + t1Ms;
                }
                if (due != null && due <= nowMs) {
                    String at = TimeUtil.now();
                    String note = "超时自动验收（满 " + config.verifyHours() + " 小时没回应）";
                    if (push(o, OrderEvent.AUTO_ACCEPT, note, x -> {
                        x.setSignAt(at);
                        x.setArchiveDueAt(plusDays(at, config.archiveDays()));
                    })) {
                        accepted.add(o.getNo());
                    } else {
                        failed.add(o.getNo());
                    }
                }
            } else if (o.getStatus() == OrderStatus.DONE) {
                Long due = TimeUtil.epochMs(o.getArchiveDueAt());
                if (due == null) {
                    Long s = TimeUtil.epochMs(o.getSignAt());
                    due = s == null ? null : s + t2Ms;
                }
                if (due != null && due <= nowMs) {
                    String note = "满 " + config.archiveDays() + " 天自动归档";
                    if (push(o, OrderEvent.ARCHIVE, note, x -> x.setClosedAt(TimeUtil.now()))) {
                        archived.add(o.getNo());
                    } else {
                        failed.add(o.getNo());
                    }
                }
            }
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("at", virtualNow);
        m.put("autoAccepted", accepted);
        m.put("archived", archived);
        m.put("failed", failed);
        return m;
    }

    private boolean push(WorkOrder o, OrderEvent event, String note, java.util.function.Consumer<WorkOrder> patch) {
        try {
            // 定时任务不认客户端的 version；失败一般是这一单刚好被人动了，下一轮会重来
            state.transit(o.getNo(), event, SYSTEM, note, null, patch);
            return true;
        } catch (RuntimeException e) {
            log.debug("定时推进 {} 失败：{}", o.getNo(), e.getMessage());
            return false;
        }
    }

    /**
     * 手动推一单到点事件（自检台和接口用）。
     *
     * <p>先判"到没到点"，再交给状态机：没到点返回 422，状态不对返回 409。
     * 这样接口有本事手动推，但推不动还没到期的表 —— 两颗计时器仍然是时间说了算。
     */
    public WorkOrder fireOne(String no, OrderEvent event) {
        WorkOrder o = store.require(no);
        long now = TimeUtil.nowMs();
        if (event == OrderEvent.AUTO_ACCEPT) {
            if (reviews.hasPending(no)) {
                throw com.zxp.dormrepair.common.BizException.precondition(
                        "返修审查中，T1 暂停", "REWORK_REVIEW_PENDING");
            }
            if (o.getStatus() == OrderStatus.VERIFYING) {
                Long due = TimeUtil.epochMs(o.getVerifyDueAt());
                if (due == null) {
                    Long f = TimeUtil.epochMs(o.getFinishAt());
                    due = (f == null ? now : f) + config.verifyHours() * 3600_000L;
                }
                if (now < due) {
                    throw com.zxp.dormrepair.common.BizException.precondition(
                            "验收倒计时还没走完，到期时间是 " + TimeUtil.fmt(due), "T1_NOT_DUE");
                }
            }
            String at = TimeUtil.now();
            state.transit(no, event, SYSTEM, "手动触发：超时自动验收", null, x -> {
                x.setSignAt(at);
                x.setArchiveDueAt(plusDays(at, config.archiveDays()));
            });
        } else if (event == OrderEvent.ARCHIVE) {
            if (o.getStatus() == OrderStatus.DONE) {
                Long due = TimeUtil.epochMs(o.getArchiveDueAt());
                if (due == null) {
                    Long s = TimeUtil.epochMs(o.getSignAt());
                    due = (s == null ? now : s) + config.archiveDays() * 24 * 3600_000L;
                }
                if (now < due) {
                    throw com.zxp.dormrepair.common.BizException.precondition(
                            "归档倒计时还没走完，到期时间是 " + TimeUtil.fmt(due), "T2_NOT_DUE");
                }
            }
            state.transit(no, event, SYSTEM, "手动触发：满期归档", null, x -> x.setClosedAt(TimeUtil.now()));
        } else {
            throw com.zxp.dormrepair.common.BizException.badRequest("这个接口只接受 AUTO_ACCEPT 与 ARCHIVE");
        }
        return store.require(no);
    }

    /** 给界面用：这颗表什么时候到期。 */
    public Map<String, Object> deadlines(WorkOrder o) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (o.getStatus() == OrderStatus.VERIFYING) {
            m.put("kind", "T1");
            m.put("from", o.getFinishAt());
            m.put("deadline", o.getVerifyDueAt() != null ? o.getVerifyDueAt()
                    : plusHours(o.getFinishAt(), config.verifyHours()));
        } else if (o.getStatus() == OrderStatus.DONE) {
            m.put("kind", "T2");
            m.put("from", o.getSignAt());
            m.put("deadline", o.getArchiveDueAt() != null ? o.getArchiveDueAt()
                    : plusDays(o.getSignAt(), config.archiveDays()));
        }
        m.put("allowed", StateMachine.allowedEvents(o.getStatus()));
        return m;
    }

    private static String plusHours(String at, int hours) {
        Long ms = TimeUtil.epochMs(at);
        return ms == null ? null : TimeUtil.fmt(ms + hours * 3600_000L);
    }

    private static String plusDays(String at, int days) {
        Long ms = TimeUtil.epochMs(at);
        return ms == null ? null : TimeUtil.fmt(ms + days * 24 * 3600_000L);
    }
}
