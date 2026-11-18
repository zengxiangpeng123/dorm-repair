package com.zxp.dormrepair.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class DatabaseSchemaGuardTest {

    @Test
    @DisplayName("完整表、视图和列通过启动门禁")
    void acceptsCompleteSchema() {
        JdbcTemplate jdbc = schemaJdbc("cleanup_claimed_at");
        assertDoesNotThrow(() -> guard(jdbc).validate());
    }

    @Test
    @DisplayName("缺少文件清理租约列时拒绝启动并指出精确字段")
    void rejectsMissingColumnWithActionableMessage() {
        JdbcTemplate jdbc = schemaJdbcWithout("t_file", "cleanup_claimed_at");
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> guard(jdbc).validate());
        assertTrue(error.getMessage().contains("t_file.cleanup_claimed_at"), error.getMessage());
        assertTrue(error.getMessage().contains("migrate.sh"), error.getMessage());
    }

    @Test
    @DisplayName("缺少业务表或人员兼容视图时拒绝启动")
    void rejectsMissingTableAndView() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyString())).thenReturn(0);
        when(jdbc.queryForList(anyString(), eq(String.class), anyString())).thenReturn(List.of());
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> guard(jdbc).validate());
        assertTrue(error.getMessage().contains("t_student"), error.getMessage());
        assertTrue(error.getMessage().contains("v_user_account"), error.getMessage());
    }

    private static DatabaseSchemaGuard guard(JdbcTemplate jdbc) {
        DataSource dataSource = mock(DataSource.class);
        DatabaseSchemaGuard guard = new DatabaseSchemaGuard(dataSource, "mysql");
        try {
            var field = DatabaseSchemaGuard.class.getDeclaredField("jdbc");
            field.setAccessible(true);
            field.set(guard, jdbc);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        return guard;
    }

    private static JdbcTemplate schemaJdbc(String... fileColumns) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            String name = invocation.getArgument(2);
            return sql.contains("TABLE_TYPE='BASE TABLE'")
                    ? (name.startsWith("t_") ? 1 : 0)
                    : (name.equals("v_user_account") ? 1 : 0);
        });
        when(jdbc.queryForList(anyString(), eq(String.class), anyString())).thenAnswer(invocation -> {
            String table = invocation.getArgument(2);
            return columnsFor(table, fileColumns);
        });
        return jdbc;
    }

    private static JdbcTemplate schemaJdbcWithout(String missingTable, String missingColumn) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyString())).thenReturn(1);
        when(jdbc.queryForList(anyString(), eq(String.class), anyString())).thenAnswer(invocation -> {
            String table = invocation.getArgument(2);
            List<String> columns = new java.util.ArrayList<>(columnsFor(table));
            if (table.equals(missingTable)) columns.remove(missingColumn);
            return columns;
        });
        return jdbc;
    }

    private static List<String> columnsFor(String table, String... fileColumns) {
        if (table.equals("t_file")) {
            List<String> cols = new java.util.ArrayList<>(List.of("id", "object_key", "original_name", "mime", "byte_size", "sha256", "uploaded_by", "purpose", "visibility", "order_no", "kind", "deleted_at", "created_at"));
            cols.addAll(List.of(fileColumns));
            return cols;
        }
        return List.of("id", "name", "phone", "team", "department", "building_id", "room", "password_hash", "active", "created_at", "updated_at", "worker_id", "is_primary", "token_hash", "account", "role", "account_id", "issued_at_ms", "expires_at_ms", "revoked_at_ms", "attempt_key", "failures", "blocked_until_ms", "updated_at_ms", "floor", "no", "status", "version", "student_id", "student_name", "description", "visit_window", "category", "level", "level_source", "ai_basis", "worker_name", "parts_name", "parts_amount", "parts_eta", "rework_of", "rework_index", "rework_count", "no_show_count", "cancel_reason", "cancel_at", "assigned_at", "arrive_at", "finish_at", "sign_at", "closed_at", "cancelled_at", "status_at", "verify_due_at", "archive_due_at", "seq", "order_no", "at", "event", "actor_type", "actor_id", "actor_name", "manual", "note", "kind", "url", "file_id", "uploaded_by", "order_version", "input_hash", "model", "provider", "attempts", "requested_at", "completed_at", "error_code", "error_message", "confidence", "basis", "source", "latency_ms", "applied", "applied_at", "spec", "unit", "price", "stock", "part_id", "part_name", "amount", "eta_date", "over_budget", "created_by", "arrived_at", "attempt_no", "requested_by", "request_reason", "decided_by", "decision_reason", "decided_at", "detail", "handled_by", "k", "v", "method", "path", "idempotency_key", "request_hash", "response_status", "response_body", "day", "next_val");
    }
}