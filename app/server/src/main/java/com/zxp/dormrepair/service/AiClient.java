package com.zxp.dormrepair.service;

import com.zxp.dormrepair.common.TimeUtil;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * 调 Python 定级服务。
 *
 * <p>两条路，超时完全不一样，别合并：
 * <ul>
 *   <li>{@link #classifyFast} —— 学生点提交那一步。只跑规则，毫秒级，1.5 秒还拿不到就兜底，
 *       绝不让学生的提交流程卡住。</li>
 *   <li>{@link #classify} —— 后台复核。真模型，单次实测 2~19 秒（同一个模型，快慢差 10 倍），
 *       所以给 30 秒，而且必须在另一个线程里跑，不能挂在学生的请求上。</li>
 * </ul>
 *
 * <p>三件不许变的事：① 拿不到就兜底，兜底也标 levelSource=FALLBACK，让人事后看得见这次是猜的；
 * ② 兜底也要给类型和紧急程度；③ AI 只回答"是哪类、多急、凭什么"，不碰工单状态。
 */
@Service
public class AiClient {

    private static final Logger log = LoggerFactory.getLogger(AiClient.class);

    /** 判不出来时用的兜底档：较急而不是普通 —— 宁可让师傅多跑一趟，也别把漏水的单压到明天。 */
    static final String FALLBACK_LEVEL = "较急";
    static final String FALLBACK_CATEGORY = "给排水";

    private final RestClient fast;
    private final RestClient slow;
    private final String baseUrl;
    private final int fastMs;
    private final int slowMs;

    public AiClient(@Value("${app.ai.base-url:http://127.0.0.1:8001}") String baseUrl,
                    @Value("${app.ai.timeout-ms:1500}") int fastMs,
                    @Value("${app.ai.review-timeout-ms:30000}") int slowMs) {
        this.baseUrl = baseUrl;
        this.fastMs = fastMs;
        this.slowMs = slowMs;
        this.fast = build(baseUrl, fastMs);
        this.slow = build(baseUrl, slowMs);
    }

    private static RestClient build(String baseUrl, int timeoutMs) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofMillis(Math.min(timeoutMs, 3000)));
        f.setReadTimeout(Duration.ofMillis(timeoutMs));
        return RestClient.builder().baseUrl(baseUrl).requestFactory(f).build();
    }

    /** 判断结果。fallback=true 表示没连上 AI，内容是兜底的。 */
    public record Verdict(String category, String level, double confidence, String basis,
                          boolean fallback, String source) {
    }

    /** 提交那一步用：只跑规则，不等模型。 */
    public Verdict classifyFast(String desc) {
        return call(fast, desc, true, "规则");
    }

    /** 后台复核用：真模型，慢。必须在异步线程里调用。 */
    public Verdict classify(String desc) {
        return call(slow, desc, false, "模型");
    }

    @SuppressWarnings("unchecked")
    private Verdict call(RestClient client, String desc, boolean onlyRules, String who) {
        long t0 = System.currentTimeMillis();
        try {
            Map<String, Object> body = client.post()
                    .uri("/classify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("desc", desc == null ? "" : desc, "fast", onlyRules))
                    .retrieve()
                    .body(Map.class);
            long ms = System.currentTimeMillis() - t0;
            if (body == null) {
                return fallback(desc, who + "返回空内容");
            }
            String category = str(body.get("category"), FALLBACK_CATEGORY);
            String level = str(body.get("level"), FALLBACK_LEVEL);
            double confidence = body.get("confidence") instanceof Number n ? n.doubleValue() : 0.0;
            String basis = str(body.get("basis"), who + "未给依据");
            // source 只用于日志：llm=真模型 / rule-gate=安全闸拦下没调模型 / rule-fast=调用方要求先给快的
            String remote = str(body.get("source"), "AI");
            log.debug("AI 定级 {}ms -> {} {} {} [{}]", ms, category, level, confidence, remote);
            return new Verdict(category, level, confidence, basis, false, "AI");
        } catch (Exception e) {
            long ms = System.currentTimeMillis() - t0;
            log.warn("AI 定级不可用（{}ms，{}），走兜底：{}", ms, e.getClass().getSimpleName(),
                    e.getMessage());
            return fallback(desc, "AI 不可用：" + e.getClass().getSimpleName());
        }
    }

    /**
     * 兜底：给一个能用的类型和保守的紧急程度，并把原因写进依据，方便事后追。
     * 兜底里也做一次最朴素的关键词判断——安全类词命中就直接紧急，细节交人工。
     */
    public Verdict fallback(String desc, String why) {
        String text = desc == null ? "" : desc;
        List<String> safety = List.of("漏电", "触电", "打火", "冒烟", "起火", "着火", "焦味");
        List<String> waterRisk = List.of("快接满", "接满了", "快满", "漫出来", "淹", "积水", "全是水");
        String level = FALLBACK_LEVEL;
        StringBuilder basis = new StringBuilder(why).append("，按保守档兜底");
        for (String w : safety) {
            if (text.contains(w)) {
                level = "紧急";
                basis.append("；描述里有「").append(w).append("」，按安全类定紧急");
                break;
            }
        }
        if (FALLBACK_LEVEL.equals(level)) {
            for (String w : waterRisk) {
                if (text.contains(w)) {
                    level = "紧急";
                    basis.append("；描述里有「").append(w).append("」，按浸水风险定紧急");
                    break;
                }
            }
        }
        basis.append("（时间 ").append(TimeUtil.now()).append("）");
        return new Verdict(FALLBACK_CATEGORY, level, 0.0, basis.toString(), true, "FALLBACK");
    }

    /** 给 /health 与看板用：AI 在不在，别等到建单才发现。 */
    public Map<String, Object> health() {
        long t0 = System.currentTimeMillis();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("baseUrl", baseUrl);
        m.put("timeoutMs", fastMs);
        m.put("reviewTimeoutMs", slowMs);
        try {
            @SuppressWarnings("rawtypes")
            Map body = fast.get().uri("/health").retrieve().body(Map.class);
            m.put("up", true);
            m.put("latencyMs", System.currentTimeMillis() - t0);
            m.put("remote", body);
        } catch (Exception e) {
            m.put("up", false);
            m.put("latencyMs", System.currentTimeMillis() - t0);
            m.put("error", e.getClass().getSimpleName());
        }
        return m;
    }

    private static String str(Object v, String dft) {
        return v == null || String.valueOf(v).isBlank() ? dft : String.valueOf(v);
    }
}
