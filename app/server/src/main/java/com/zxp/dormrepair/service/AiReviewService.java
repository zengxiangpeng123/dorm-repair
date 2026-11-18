package com.zxp.dormrepair.service;

import com.zxp.dormrepair.domain.WorkOrder;
import com.zxp.dormrepair.statemachine.OrderStatus;
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
    private final ExecutorService pool;

    public AiReviewService(AiClient ai, OrderRepository store) {
        this.ai = ai;
        this.store = store;
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
        pool.submit(() -> {
            try {
                AiClient.Verdict v = ai.classify(desc);
                if (v.fallback()) {
                    log.debug("AI 复核 {}：模型没给结果，保留规则定级", no);
                    return;
                }
                WorkOrder cur = store.get(no);
                if (cur == null || !Set.of(OrderStatus.PENDING, OrderStatus.ASSIGNED)
                        .contains(cur.getStatus())) {
                    log.debug("AI 复核 {}：工单已进入后续阶段，丢弃晚到结果", no);
                    return;
                }
                // 带当前 version 做 CAS：复核期间若工单被派工或推进，更新失败并保留最新业务状态。
                boolean ok = store.update(no, cur.getVersion(), o -> {
                    o.setCategory(v.category());
                    o.setLevel(v.level());
                    o.setAiBasis(v.basis());
                    o.setLevelSource("AI");
                });
                log.info("AI 复核 {} -> {} / {}（{}）", no, v.category(), v.level(),
                        ok ? "已写回" : "写回失败");
            } catch (Exception e) {
                log.warn("AI 复核 {} 出错，保留规则定级：{}", no, e.toString());
            }
        });
    }

    @PreDestroy
    void shutdown() {
        pool.shutdownNow();
    }
}
