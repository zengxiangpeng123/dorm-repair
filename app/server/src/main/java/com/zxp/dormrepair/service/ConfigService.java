package com.zxp.dormrepair.service;

import com.zxp.dormrepair.store.mapper.ConfigMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 可调参数：预算线、验收超时、归档天数。
 *
 * <p>规矩 C-11 / T-08：这三个数不许写死在业务代码里。True 数据库时读 t_config，
 * 读不到（或跑内存单测时）用默认值兜住 —— 演示环境不该因为少一行配置就跑不起来。
 */
@Service
public class ConfigService {

    private static final Logger log = LoggerFactory.getLogger(ConfigService.class);

    public static final String BUDGET_LINE = "budgetLine";
    public static final String VERIFY_HOURS = "verifyHours";
    public static final String ARCHIVE_DAYS = "archiveDays";

    private static final Map<String, String> DEFAULTS = Map.of(
            BUDGET_LINE, "200",
            VERIFY_HOURS, "168",
            ARCHIVE_DAYS, "7");

    private final ConfigMapper mapper;
    private final boolean useDb;
    private final Map<String, String> cache = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile long loadedAtMs;

    public ConfigService(ConfigMapper mapper, @Value("${app.store:mysql}") String store) {
        this.mapper = mapper;
        this.useDb = "mysql".equalsIgnoreCase(store);
        reload();
    }

    public synchronized void reload() {
        cache.clear();
        if (!useDb) {
            cache.putAll(DEFAULTS);
            return;
        }
        try {
            for (Map<String, Object> row : mapper.all()) {
                cache.put(String.valueOf(row.get("k")), String.valueOf(row.get("v")));
            }
            loadedAtMs = System.currentTimeMillis();
        } catch (Exception e) {
            log.warn("读 t_config 失败，先用默认值：{}", e.getMessage());
        }
        DEFAULTS.forEach(cache::putIfAbsent);
    }

    public int budgetLine() {
        refreshIfStale();
        return intOf(BUDGET_LINE);
    }

    public int verifyHours() {
        refreshIfStale();
        return intOf(VERIFY_HOURS);
    }

    public int archiveDays() {
        refreshIfStale();
        return intOf(ARCHIVE_DAYS);
    }

    /** 多实例修改 t_config 后，其他实例最多 60 秒内看到新值。 */
    private void refreshIfStale() {
        if (useDb && System.currentTimeMillis() - loadedAtMs > 60_000L) {
            reload();
        }
    }

    /** 给 /meta 和前端配置页用 */
    public Map<String, Object> all() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(BUDGET_LINE, budgetLine());
        m.put(VERIFY_HOURS, verifyHours());
        m.put(ARCHIVE_DAYS, archiveDays());
        m.put("source", useDb ? "t_config" : "default");
        return m;
    }

    public Map<String, Object> update(Map<String, Object> patch) {
        if (useDb) {
            patch.forEach((k, v) -> {
                if (DEFAULTS.containsKey(k)) {
                    mapper.upsert(k, String.valueOf(v), com.zxp.dormrepair.common.TimeUtil.now());
                }
            });
        }
        patch.forEach((k, v) -> {
            if (DEFAULTS.containsKey(k)) cache.put(k, String.valueOf(v));
        });
        loadedAtMs = System.currentTimeMillis();
        return all();
    }

    private int intOf(String key) {
        try {
            return Integer.parseInt(cache.getOrDefault(key, DEFAULTS.get(key)));
        } catch (NumberFormatException e) {
            return Integer.parseInt(DEFAULTS.get(key));
        }
    }
}
