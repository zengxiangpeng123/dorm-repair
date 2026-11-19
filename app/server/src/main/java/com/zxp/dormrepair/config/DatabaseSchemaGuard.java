package com.zxp.dormrepair.config;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.SmartInitializingSingleton;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 启动时确认数据库已执行当前版本迁移，缺表或缺关键列则拒绝带病启动。 */
@Component
public class DatabaseSchemaGuard implements SmartInitializingSingleton {

    private static final List<TableSchema> REQUIRED_SCHEMA = List.of(
            table("t_student", "id", "name", "phone", "building_id", "room", "password_hash", "active", "created_at", "updated_at"),
            table("t_worker", "id", "name", "phone", "team", "password_hash", "active", "created_at", "updated_at"),
            table("t_admin", "id", "name", "phone", "department", "password_hash", "active", "created_at", "updated_at"),
            table("t_worker_building", "worker_id", "building_id", "is_primary"),
            table("t_auth_session", "token_hash", "account", "role", "name", "account_id", "issued_at_ms", "expires_at_ms", "revoked_at_ms"),
            table("t_login_attempt", "attempt_key", "failures", "blocked_until_ms", "updated_at_ms"),
            table("t_building", "id", "name"),
            table("t_building_floor", "building_id", "floor"),
            table("t_work_order", "no", "status", "version", "building_id", "floor", "room", "student_id", "student_name", "description", "visit_window", "category", "level", "level_source", "ai_basis", "worker_id", "worker_name", "parts_name", "parts_amount", "parts_eta", "rework_of", "rework_index", "rework_count", "no_show_count", "cancel_reason", "cancel_at", "created_at", "assigned_at", "arrive_at", "finish_at", "sign_at", "closed_at", "cancelled_at", "status_at", "verify_due_at", "archive_due_at"),
            table("t_work_order_event", "seq", "order_no", "at", "event", "actor_type", "actor_id", "actor_name", "manual", "note"),
            table("t_work_order_photo", "id", "order_no", "kind", "url", "file_id", "uploaded_by", "created_at"),
            table("t_file", "id", "object_key", "original_name", "mime", "byte_size", "sha256", "uploaded_by", "purpose", "visibility", "order_no", "kind", "deleted_at", "cleanup_claimed_at", "created_at"),
            table("t_ai_review_task", "id", "order_no", "order_version", "input_hash", "model", "provider", "status", "attempts", "requested_at", "completed_at", "error_code", "error_message", "category", "level", "confidence", "basis", "source", "latency_ms", "applied", "applied_at"),
            table("t_part", "id", "name", "spec", "unit", "price", "stock", "updated_at"),
            table("t_part_request", "id", "order_no", "part_id", "part_name", "amount", "eta_date", "over_budget", "status", "created_by", "created_at", "arrived_at"),
            table("t_rework_review", "id", "order_no", "attempt_no", "rework_index", "status", "requested_by", "request_reason", "requested_at", "decided_by", "decision_reason", "decided_at"),
            table("t_exception_record", "id", "kind", "order_no", "detail", "status", "handled_by", "created_at", "closed_at"),
            table("t_config", "k", "v", "note", "updated_at"),
            table("t_idempotency_record", "id", "actor_type", "actor_id", "method", "path", "idempotency_key", "request_hash", "status", "response_status", "response_body", "created_at", "completed_at"),
            table("t_seq", "day", "name", "next_val"));

    private static final List<String> REQUIRED_VIEWS = List.of("v_user_account");

    private final JdbcTemplate jdbc;
    private final boolean enabled;

    public DatabaseSchemaGuard(DataSource dataSource,
                               @Value("${app.store:mysql}") String store) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.enabled = "mysql".equalsIgnoreCase(store);
    }

    /**
     * 在 Web 服务器开始接收请求之前校验结构。ApplicationRunner 发生在端口开放之后，
     * 会让错误配置出现一段“健康端点已可用、随后进程退出”的假就绪窗口。
     */
    @Override
    public void afterSingletonsInstantiated() {
        if (!enabled) {
            return;
        }
        validate();
    }

    void validate() {
        List<String> missingTables = REQUIRED_SCHEMA.stream()
                .map(TableSchema::name)
                .filter(table -> !tableExists(table))
                .toList();
        List<String> missingViews = REQUIRED_VIEWS.stream()
                .filter(view -> !viewExists(view))
                .toList();
        Map<String, List<String>> actualColumns = REQUIRED_SCHEMA.stream()
                .filter(table -> !missingTables.contains(table.name()))
                .collect(Collectors.toMap(TableSchema::name,
                        table -> columnsOf(table.name())));
        List<String> missingColumns = REQUIRED_SCHEMA.stream()
                .filter(table -> actualColumns.containsKey(table.name()))
                .flatMap(table -> table.columns().stream()
                        .filter(column -> !actualColumns.get(table.name()).contains(column))
                        .map(column -> table.name() + "." + column))
                .toList();
        if (!missingTables.isEmpty() || !missingViews.isEmpty() || !missingColumns.isEmpty()) {
            throw new IllegalStateException("数据库结构未升级，缺少："
                    + "表[" + String.join(", ", missingTables) + "] "
                    + "视图[" + String.join(", ", missingViews) + "] "
                    + "列[" + String.join(", ", missingColumns) + "]"
                    + "；请先执行 deploy/scripts/migrate.sh");
        }
    }

    private boolean tableExists(String table) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES " +
                        "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND TABLE_TYPE='BASE TABLE'",
                Integer.class, table);
        return n != null && n > 0;
    }

    private boolean viewExists(String view) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.VIEWS " +
                        "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?",
                Integer.class, view);
        return n != null && n > 0;
    }

    private List<String> columnsOf(String table) {
        return jdbc.queryForList(
                "SELECT COLUMN_NAME FROM information_schema.COLUMNS " +
                        "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?",
                String.class, table);
    }

    private static TableSchema table(String name, String... columns) {
        return new TableSchema(name, List.of(columns));
    }

    private record TableSchema(String name, List<String> columns) {
    }
}
