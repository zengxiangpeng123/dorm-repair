package com.zxp.dormrepair.service;

import com.zxp.dormrepair.common.BizException;
import com.zxp.dormrepair.common.TimeUtil;
import com.zxp.dormrepair.domain.Actor;
import com.zxp.dormrepair.domain.IdempotencyRecord;
import com.zxp.dormrepair.store.IdempotencyRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 关键写命令的幂等协调器：先原子占位，成功写结果，失败释放。 */
@Service
public class IdempotencyService {
    private final IdempotencyRepository records;
    @org.springframework.beans.factory.annotation.Value("${app.idempotency.claim-timeout-minutes:10}")
    private int claimTimeoutMinutes = 10;

    public IdempotencyService(IdempotencyRepository records) {
        this.records = records;
    }

    /** 返回 {replay, owner}；owner 非空表示本请求持有执行权。 */
    public Claim begin(Actor actor, String method, String path, String key, String requestHash) {
        if (key == null || key.isBlank()) return null;
        String actorType = actor.type().name();
        String actorId = actor.id();
        String staleBefore = TimeUtil.fmt(TimeUtil.nowMs()
                - Math.max(1, claimTimeoutMinutes) * 60_000L);
        var found = records.find(actorType, actorId, method, path, key.trim());
        if (found.isPresent()) {
            IdempotencyRecord row = found.get();
            if (!row.requestHash().equals(requestHash)) {
                throw BizException.conflict("IDEMPOTENCY_KEY_REUSED",
                        "同一个 Idempotency-Key 不能用于不同请求内容");
            }
            if ("COMPLETED".equals(row.status())) return new Claim(row, null);
            Long created = TimeUtil.epochMs(row.createdAt());
            Long cutoff = TimeUtil.epochMs(staleBefore);
            if (created == null || cutoff == null || created >= cutoff) {
                throw BizException.conflict("IDEMPOTENCY_IN_PROGRESS",
                        "相同请求正在处理中，请稍后重试");
            }
        }
        IdempotencyRecord claimed = new IdempotencyRecord(UUID.randomUUID().toString(), actorType,
                actorId, method, path, key.trim(), requestHash, "PROCESSING", null, null,
                TimeUtil.now(), null);
        if (!records.claim(claimed, staleBefore)) {
            throw BizException.conflict("IDEMPOTENCY_IN_PROGRESS",
                    "相同请求正在处理中，请稍后重试");
        }
        return new Claim(null, claimed);
    }

    public record Claim(IdempotencyRecord replay, IdempotencyRecord owner) {}

    public void complete(IdempotencyRecord owner, int status, String body) {
        if (owner == null) return;
        records.complete(owner.id(), status, body, TimeUtil.now());
    }

    public void release(IdempotencyRecord owner) {
        if (owner == null) return;
        records.release(owner.id());
    }

    public static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /** 规范化 JSON：对象键排序、忽略空白；非 JSON 请求退回原始文本。 */
    public static String canonicalHash(String method, String path, String body) {
        String canonical = body == null ? "" : body;
        try {
            canonical = sort(JsonMapper.builder().build().readTree(canonical));
        } catch (Exception ignored) {
            // 表单等非 JSON 请求保持原文本语义。
        }
        return hash(method + "\n" + path + "\n" + canonical);
    }

    private static String sort(JsonNode node) {
        if (node == null || node.isNull()) return "null";
        if (node.isObject()) {
            TreeMap<String, String> values = new TreeMap<>();
            node.properties().forEach(e -> values.put(e.getKey(), sort(e.getValue())));
            StringBuilder out = new StringBuilder("{");
            values.forEach((k, v) -> {
                if (out.length() > 1) out.append(',');
                out.append(JsonMapper.builder().build().writeValueAsString(k)).append(':').append(v);
            });
            return out.append('}').toString();
        }
        if (node.isArray()) {
            StringBuilder out = new StringBuilder("[");
            node.forEach(v -> {
                if (out.length() > 1) out.append(',');
                out.append(sort(v));
            });
            return out.append(']').toString();
        }
        if (node.isTextual()) return JsonMapper.builder().build().writeValueAsString(node.asText());
        return node.toString();
    }

    public void clear() {
        records.clear();
    }
}
