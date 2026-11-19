package com.zxp.dormrepair.store;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 单进程测试实现；生产的多实例互斥由 MySQL 咨询锁保证。 */
@Component
@ConditionalOnProperty(name = "app.store", havingValue = "memory", matchIfMissing = true)
public class InMemoryScheduledTaskLock implements ScheduledTaskLock {
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    @Override
    public boolean runExclusive(String task, long timeoutSeconds, Runnable action) {
        ReentrantLock lock = locks.computeIfAbsent(task, ignored -> new ReentrantLock());
        if (!lock.tryLock()) {
            return false;
        }
        try {
            action.run();
            return true;
        } finally {
            lock.unlock();
        }
    }
}
