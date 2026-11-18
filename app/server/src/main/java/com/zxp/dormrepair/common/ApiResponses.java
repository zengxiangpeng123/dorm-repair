package com.zxp.dormrepair.common;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 出参只有两种形状：{ok:true,data:...} 与 {ok:false,error:{code,message,...}}。
 */
public final class ApiResponses {

    private ApiResponses() {
    }

    public static Map<String, Object> ok(Object data) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("data", data);
        return m;
    }

    public static Map<String, Object> error(String code, String message) {
        return error(code, message, Map.of());
    }

    public static Map<String, Object> error(String code, String message, Map<String, Object> extra) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("code", code);
        err.put("message", message);
        if (extra != null) {
            extra.forEach((k, v) -> {
                if (v != null) {
                    err.put(k, v);
                }
            });
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", false);
        m.put("error", err);
        return m;
    }
}
