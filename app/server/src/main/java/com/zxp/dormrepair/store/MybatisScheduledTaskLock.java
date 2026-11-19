package com.zxp.dormrepair.store;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * MySQL GET_LOCK 实现。锁绑定在具体连接上，因此使用一条专用连接持锁；
 * 业务操作不加入这条锁事务，避免文件清理等外部 I/O 形成长事务。
 */
@Component
@ConditionalOnProperty(name = "app.store", havingValue = "mysql")
public class MybatisScheduledTaskLock implements ScheduledTaskLock {
    private final DataSource dataSource;

    public MybatisScheduledTaskLock(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean runExclusive(String task, long timeoutSeconds, Runnable action) {
        String lockName = "dorm-scheduler-" + task;
        try (Connection connection = dataSource.getConnection()) {
            if (!acquire(connection, lockName, Math.max(0, timeoutSeconds))) {
                return false;
            }
            try {
                action.run();
                return true;
            } finally {
                release(connection, lockName);
            }
        } catch (Exception e) {
            throw new IllegalStateException("调度锁执行失败：" + task, e);
        }
    }

    private static boolean acquire(Connection connection, String lockName, long timeoutSeconds)
            throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("SELECT GET_LOCK(?, ?)")) {
            ps.setString(1, lockName);
            ps.setLong(2, timeoutSeconds);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) == 1;
            }
        }
    }

    private static void release(Connection connection, String lockName) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            ps.setString(1, lockName);
            ps.executeQuery().close();
        }
    }
}
