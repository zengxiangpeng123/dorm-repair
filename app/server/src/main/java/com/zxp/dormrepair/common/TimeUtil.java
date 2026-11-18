package com.zxp.dormrepair.common;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 全站时间只有一个写法：yyyy-MM-dd HH:mm:ss，东八区，不带毫秒不带时区后缀。
 * 规矩 T-09：页面、接口、日志都用它，别各写一套。
 */
public final class TimeUtil {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private TimeUtil() {
    }

    public static String now() {
        return LocalDateTime.now(ZONE).format(FMT);
    }

    public static String fmt(LocalDateTime t) {
        return t == null ? null : t.atZone(ZONE).format(FMT);
    }

    public static LocalDateTime parse(String s) {
        return s == null || s.isBlank() ? null : LocalDateTime.parse(s, FMT);
    }
}
