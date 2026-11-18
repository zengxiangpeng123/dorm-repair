package com.zxp.dormrepair.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * 业务异常。抛它就等于给前端一个 {ok:false,error:{code,message,...extra}}。
 */
public class BizException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> extra = new LinkedHashMap<>();

    public BizException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static BizException badRequest(String message) {
        return new BizException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public static BizException unauthorized(String message) {
        return new BizException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }

    public static BizException forbidden(String message) {
        return new BizException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public static BizException notOwner(String message) {
        return new BizException(HttpStatus.FORBIDDEN, "NOT_OWNER", message);
    }

    public static BizException notFound(String message) {
        return new BizException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    /** 状态机里没有这条边。必须带上 from/event/allowed，前端靠它画按钮。 */
    public static BizException stateConflict(String message, String from, String event, Object allowed) {
        return new BizException(HttpStatus.CONFLICT, "STATE_CONFLICT", message)
                .with("reason", "NOT_ALLOWED")
                .with("from", from)
                .with("event", event)
                .with("allowed", allowed);
    }

    public static BizException versionConflict(int expect, int got, Object allowed) {
        return new BizException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "状态已变化，请刷新")
                .with("reason", "VERSION_CONFLICT")
                .with("expectVersion", expect)
                .with("gotVersion", got)
                .with("allowed", allowed);
    }

    /** 有这条边，但前置不满足（照片没传、缺原因）。 */
    public static BizException precondition(String message, String reason) {
        return new BizException(HttpStatus.UNPROCESSABLE_ENTITY, "PRECONDITION_FAILED", message)
                .with("reason", reason);
    }

    /** 还没批准的功能：一律 501，并说清等哪张变更单。 */
    public static BizException notImplemented(String message, String changeRequest) {
        return new BizException(HttpStatus.NOT_IMPLEMENTED, "NOT_IMPLEMENTED", message)
                .with("changeRequest", changeRequest);
    }

    public BizException with(String key, Object value) {
        extra.put(key, value);
        return this;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Map<String, Object> extra() {
        return extra;
    }
}
