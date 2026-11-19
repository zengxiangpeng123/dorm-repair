package com.zxp.dormrepair;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxp.dormrepair.store.InMemoryScheduledTaskLock;
import com.zxp.dormrepair.store.MybatisScheduledTaskLock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ScheduledTaskLockTest {

    @Test
    @DisplayName("内存锁正在执行时，第二个实例跳过本轮而不是重复执行")
    void inMemoryLockSkipsConcurrentRun() throws Exception {
        var lock = new InMemoryScheduledTaskLock();
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var runs = new AtomicInteger();
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> lock.runExclusive("timer-sweep", 0, () -> {
                runs.incrementAndGet();
                started.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertFalse(lock.runExclusive("timer-sweep", 0, runs::incrementAndGet));
            release.countDown();
            assertTrue(first.get(5, TimeUnit.SECONDS));
            assertTrue(runs.get() == 1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("MySQL 锁获取失败时跳过任务，也不释放其他实例持有的锁")
    void mysqlLockSkipsWhenNotAcquired() throws Exception {
        var connection = org.mockito.Mockito.mock(java.sql.Connection.class);
        var acquire = org.mockito.Mockito.mock(java.sql.PreparedStatement.class);
        var result = org.mockito.Mockito.mock(java.sql.ResultSet.class);
        org.mockito.Mockito.when(connection.prepareStatement("SELECT GET_LOCK(?, ?)")).thenReturn(acquire);
        org.mockito.Mockito.when(acquire.executeQuery()).thenReturn(result);
        org.mockito.Mockito.when(result.next()).thenReturn(true);
        org.mockito.Mockito.when(result.getInt(1)).thenReturn(0);
        var dataSource = org.mockito.Mockito.mock(javax.sql.DataSource.class);
        org.mockito.Mockito.when(dataSource.getConnection()).thenReturn(connection);
        var lock = new MybatisScheduledTaskLock(dataSource);
        var runs = new AtomicInteger();

        assertFalse(lock.runExclusive("timer-sweep", 0, runs::incrementAndGet));
        assertTrue(runs.get() == 0);
        org.mockito.Mockito.verify(connection, org.mockito.Mockito.never())
                .prepareStatement("SELECT RELEASE_LOCK(?)");
    }

    @Test
    @DisplayName("MySQL 锁获取成功后执行并释放")
    void mysqlLockReleasesAfterRun() throws Exception {
        var connection = org.mockito.Mockito.mock(java.sql.Connection.class);
        var acquire = org.mockito.Mockito.mock(java.sql.PreparedStatement.class);
        var release = org.mockito.Mockito.mock(java.sql.PreparedStatement.class);
        var result = org.mockito.Mockito.mock(java.sql.ResultSet.class);
        var released = org.mockito.Mockito.mock(java.sql.ResultSet.class);
        org.mockito.Mockito.when(connection.prepareStatement("SELECT GET_LOCK(?, ?)")).thenReturn(acquire);
        org.mockito.Mockito.when(connection.prepareStatement("SELECT RELEASE_LOCK(?)")).thenReturn(release);
        org.mockito.Mockito.when(acquire.executeQuery()).thenReturn(result);
        org.mockito.Mockito.when(release.executeQuery()).thenReturn(released);
        org.mockito.Mockito.when(result.next()).thenReturn(true);
        org.mockito.Mockito.when(result.getInt(1)).thenReturn(1);
        var dataSource = org.mockito.Mockito.mock(javax.sql.DataSource.class);
        org.mockito.Mockito.when(dataSource.getConnection()).thenReturn(connection);
        var lock = new MybatisScheduledTaskLock(dataSource);
        var runs = new AtomicInteger();

        assertTrue(lock.runExclusive("timer-sweep", 0, runs::incrementAndGet));
        assertTrue(runs.get() == 1);
        org.mockito.Mockito.verify(release).executeQuery();
    }
}
