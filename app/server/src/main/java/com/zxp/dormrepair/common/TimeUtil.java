package com.zxp.dormrepair.common;

import java.time.Instant;
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

    /** 现在往前推 n 分钟的时间串。演示数据的"两小时前"就是这么来的。 */
/** 毫秒时间戳。算倒计时都用它，别在业务里到处 LocalDateTime.toEpochSecond 绕。 */
    public static long nowMs() {
        return System.currentTimeMillis();
    }

    /** 字符串时间 → 时间戳；解析不了给 null（宁可少算一颗表，也别把接口打 500） */
    public static Long epochMs(String s) {
        LocalDateTime t = parse(s);
        return t == null ? null : t.atZone(ZONE).toInstant().toEpochMilli();
    }

    public static String fmt(long ms) {
        return fmt(LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZONE));
    }

    public static String agoMinutes(int minutes) {
        return LocalDateTime.now(ZONE).minusMinutes(minutes).format(FMT);
    }

    /** 今天往后推 n 天的日期串（配件到货日这种只有日期的）。 */
    public static String dateAfterDays(int days) {
        return LocalDateTime.now(ZONE).plusDays(days).toLocalDate().toString();
    }
}
