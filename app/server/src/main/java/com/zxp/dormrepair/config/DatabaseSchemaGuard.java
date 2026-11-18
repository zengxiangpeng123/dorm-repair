package com.zxp.dormrepair.config;

import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 启动时确认数据库已执行当前版本迁移，缺表则拒绝带病启动。 */
@Component
public class DatabaseSchemaGuard implements ApplicationRunner {

    private static final List<String> REQUIRED = List.of(
            "t_student", "t_worker", "t_admin", "t_worker_building",
            "t_auth_session", "t_login_attempt", "t_file", "t_ai_review_task",
            "t_rework_review", "t_idempotency_record");

    private final JdbcTemplate jdbc;
    private final boolean enabled;

    public DatabaseSchemaGuard(DataSource dataSource,
                               @Value("${app.store:mysql}") String store) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.enabled = "mysql".equalsIgnoreCase(store);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        List<String> missing = REQUIRED.stream().filter(t -> !exists(t)).toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("数据库结构未升级，缺少表：" + String.join(", ", missing)
                    + "；请先执行 deploy/scripts/migrate.sh");
        }
    }

    private boolean exists(String table) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES " +
                        "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?",
                Integer.class, table);
        return n != null && n > 0;
    }
}
