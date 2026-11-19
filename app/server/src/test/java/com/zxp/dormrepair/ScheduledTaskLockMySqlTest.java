package com.zxp.dormrepair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxp.dormrepair.store.MybatisScheduledTaskLock;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 真实 MySQL 验证：锁在专用连接上持有，业务操作结束后释放；
 * 另一连接在持有期间不能取得同名锁。测试不写业务表。
 */
@SpringBootTest(properties = {
        "app.store=mysql",
        "spring.datasource.url=jdbc:mysql://localhost:3306/dorm_repair_test?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true",
        "spring.datasource.username=root",
        "spring.datasource.password=123456",
        "app.demo=false"
})
class ScheduledTaskLockMySqlTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("真实 MySQL：专用连接持锁，另一连接获取失败，释放后可取得")
    void advisoryLockIsHeldOnOneConnection() throws Exception {
        Assumptions.assumeTrue("true".equalsIgnoreCase(System.getenv("RUN_MYSQL_LOCK_TEST")),
                "set RUN_MYSQL_LOCK_TEST=true to run against local MySQL");
        String task = "integration-" + System.nanoTime();
        var lock = new MybatisScheduledTaskLock(dataSource);
        boolean ran = lock.runExclusive(task, 0, () -> {
            try (Connection other = DriverManager.getConnection(
                    "jdbc:mysql://localhost:3306/dorm_repair_test?useSSL=false&allowPublicKeyRetrieval=true",
                    "root", "123456");
                 Statement st = other.createStatement();
                 ResultSet rs = st.executeQuery("SELECT GET_LOCK('dorm-scheduler-" + task + "',0)")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        assertTrue(ran);
        try (Connection other = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/dorm_repair_test?useSSL=false&allowPublicKeyRetrieval=true",
                "root", "123456");
             Statement st = other.createStatement();
             ResultSet rs = st.executeQuery("SELECT IS_FREE_LOCK('dorm-scheduler-" + task + "')")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }
    }
}
