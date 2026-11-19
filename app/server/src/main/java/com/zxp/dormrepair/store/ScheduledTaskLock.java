package com.zxp.dormrepair.store;

/** 多个应用实例共享的定时任务锁；拿不到锁表示本轮由其他实例执行。 */
public interface ScheduledTaskLock {
    /**
     * 尝试独占执行一次维护任务。
     *
     * @return true 表示本轮获得了执行权；false 表示其他实例正在执行，本轮跳过
     */
    boolean runExclusive(String task, long timeoutSeconds, Runnable action);
}
