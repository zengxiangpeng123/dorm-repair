package com.zxp.dormrepair.store;

import java.sql.CallableStatement;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/**
 * 把「yyyy-MM-dd HH:mm:ss」的字符串和 MySQL 的 DATETIME 互相换。
 *
 * <p>为什么不用 LocalDateTime 当字段类型：M1 的内存实现、接口契约、前端全是这个字符串，
 * 换类型要动三层；一个 TypeHandler 就够，别为了"类型正确"去改契约。
 *
 * <p>顺带支持只有日期的情况（配件到货日 parts_eta 是 DATE 列）。
 */
public class DateTimeStringTypeHandler extends BaseTypeHandler<String> {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        String v = parameter.trim();
        if (v.length() == 10) {
            ps.setDate(i, Date.valueOf(v));
        } else {
            ps.setTimestamp(i, Timestamp.valueOf(v));
        }
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return read(rs, columnName);
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return read(rs, columnIndex);
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        Object v = cs.getObject(columnIndex);
        return fmt(v);
    }

    private String read(ResultSet rs, String columnName) throws SQLException {
        return fmt(rs.getObject(columnName));
    }

    private String read(ResultSet rs, int columnIndex) throws SQLException {
        return fmt(rs.getObject(columnIndex));
    }

    private String fmt(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Timestamp ts) {
            return ts.toLocalDateTime().format(DATE_TIME);
        }
        if (v instanceof LocalDateTime dt) {
            return dt.format(DATE_TIME);
        }
        if (v instanceof Date d) {
            return d.toLocalDate().toString();
        }
        if (v instanceof LocalDate ld) {
            return ld.toString();
        }
        String s = String.valueOf(v);
        return s.endsWith(" 00:00:00") ? s.substring(0, 10) : s;
    }

    /** 空串当 null 写，别让 MySQL 在 DATETIME 上收到 ''。 */
    @Override
    public void setParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        if (parameter == null || parameter.isBlank()) {
            ps.setNull(i, Types.TIMESTAMP);
        } else {
            setNonNullParameter(ps, i, parameter, jdbcType);
        }
    }
}
