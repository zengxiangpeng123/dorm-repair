package com.zxp.dormrepair.service;

import com.zxp.dormrepair.common.TimeUtil;
import com.zxp.dormrepair.domain.AiReviewTask;
import com.zxp.dormrepair.domain.AiReviewTaskStatus;
import com.zxp.dormrepair.domain.WorkOrder;
import com.zxp.dormrepair.statemachine.OrderStatus;
import com.zxp.dormrepair.store.AiReviewTaskRepository;
import com.zxp.dormrepair.store.OrderRepository;
import java.util.Set;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 后台的 AI 复核。
 *
 * <p>为什么不在提交时同步等模型：接的这个模型单次要 2~19 秒（同一个模型，两次能差 10 倍），
 * 而学生点"提交"绝不能站着等 19 秒。所以拆成两步：
 * 提交时用规则立刻定级（毫秒级），单子马上建好、马上能派工；模型在后台慢慢看，
 * 看完了把更准的类型和紧急程度写回去，界面上只是"定级更准了"，工单状态一步没动。
 *
 * <p>线程池是守护线程、只有 2 个：模型是外部依赖，它慢不能拖垮 Web 线程和数据库连接。
 */
@Service
public class AiReviewService {

    private static final Logger log = LoggerFactory.getLogger(AiReviewService.class);

    private final AiClient ai;
    private final OrderRepository store;
    private final AiReviewTaskRepository tasks;
    private final ExecutorService pool;

    public AiReviewService(AiClient ai, OrderRepository store, AiReviewTaskRepository tasks) {
        this.ai = ai;
        this.store = store;
        this.tasks = tasks;
        AtomicInteger seq = new AtomicInteger();
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "ai-review-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        this.pool = Executors.newFixedThreadPool(2, tf);
    }

    /**
     * 把一单丢给模型复核。立刻返回，不阻塞调用方。
     *
     * <p>只改 category / level / aiBasis / levelSource 四个字段，**不碰状态、不写事件**——
     * 状态机还是只有 Java 那 13 条边，AI 说错话顶多定级不准。
     */
    public void review(String no, String desc) {
        WorkOrder snapshot = store.get(no);
        if (snapshot == null) return;
        review(no, snapshot.getVersion(), desc);
    }

    /** 显式传入任务快照版本；调用方必须在所有同步建单动作完成后取值。 */
    public void review(String no, int orderVersion, String desc) {
        String inputHash = sha256(desc == null ? "" : desc);
        AiReviewTask task = tasks.create(no, orderVersion, inputHash, ai.model(), ai.provider());
        pool.submit(() -> runTask(task, desc));
    }

    /** 同步执行一次已创建任务，供后台线程和确定性测试共用。 */
    public void runTask(AiReviewTask task, String desc) {
        String no = task.orderNo();
        {
            int attempts = 1;
            try {
                tasks.markRunning(task.id(), attempts);
                AiClient.Verdict v = ai.classify(desc);
                attempts = Math.max(1, v.attempts());
                AiReviewTaskStatus resultStatus = v.fallback()
                        ? AiReviewTaskStatus.FALLBACK : AiReviewTaskStatus.SUCCEEDED;
                tasks.markResult(task.id(), resultStatus, attempts, v.errorCode(), v.errorMessage(),
                        v.category(), v.level(), v.confidence(), v.basis(), v.source(), v.latencyMs());
                if (v.fallback()) {
                    log.debug("AI 复核 {}：模型没给结果，保留规则定级", no);
                    return;
                }
                WorkOrder cur = store.get(no);
                if (cur == null || !Set.of(OrderStatus.PENDING, OrderStatus.ASSIGNED)
                        .contains(cur.getStatus())) {
                    tasks.markSuperseded(task.id(), "工单已进入后续状态");
                    log.debug("AI 复核 {}：工单已进入后续阶段，丢弃晚到结果", no);
                    return;
                }
                if (cur.getVersion() != task.orderVersion()) {
                    tasks.markSuperseded(task.id(), "工单版本已变化");
                    log.debug("AI 复核 {}：任务版本 {} 已过期，当前 {}", no, task.orderVersion(), cur.getVersion());
                    return;
                }
                boolean ok = store.update(no, task.orderVersion(), o -> {
                    o.setCategory(v.category());
                    o.setLevel(v.level());
                    o.setAiBasis(v.basis());
                    o.setLevelSource("AI");
                });
                if (ok) tasks.markApplied(task.id(), TimeUtil.now());
                else tasks.markSuperseded(task.id(), "CAS 写回失败");
                log.info("AI 复核 {} -> {} / {}（{}）", no, v.category(), v.level(),
                        ok ? "已写回" : "写回失败");
            } catch (Exception e) {
                tasks.markResult(task.id(), AiReviewTaskStatus.FAILED, attempts, "AI_REVIEW_EXCEPTION",
                        e.toString(), null, null, null, null, null, 0L);
                log.warn("AI 复核 {} 出错，保留规则定级：{}", no, e.toString());
            }
        }
    }

    private static String sha256(String text) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    @PreDestroy
    void shutdown() {
        pool.shutdownNow();
    }
}
