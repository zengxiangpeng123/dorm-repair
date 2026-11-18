package com.zxp.dormrepair.store;

import com.zxp.dormrepair.domain.OrderEventLog;
import com.zxp.dormrepair.domain.WorkOrder;
import java.util.List;
import java.util.function.Consumer;

/**
 * 工单仓库。M1 是内存（{@link OrderStore}），M2 起是 MySQL（{@link MybatisOrderRepository}）。
 *
 * <p>换实现只改 {@code app.store} 这一个配置，service 一行都不动。
 */
public interface OrderRepository {

    /** 取一份拷贝；没有返回 null。 */
    WorkOrder get(String no);

    /** 取一份拷贝；没有抛 404。 */
    WorkOrder require(String no);

    /** 全部工单，按创建时间倒序。 */
    List<WorkOrder> all();

    /** 管理端分页：数据库/内存只加载当前页及本页明细。 */
    Page page(String status, int page, int pageSize);

    /** 定时器专用：只返回仍可能到期的待验收与已完成工单。 */
    List<WorkOrder> findTimerCandidates();

    /** 新单入库（含照片）。 */
    void save(WorkOrder o);

    /**
     * 带版本号的一次改写。expectVersion 为 null 表示不校验（系统事件）。
     *
     * @return false 表示版本对不上，调用方要抛 409 VERSION_CONFLICT
     */
    boolean update(String no, Integer expectVersion, Consumer<WorkOrder> mutator);

    /** 内存实现用；MySQL 实现返回 0（序号由自增主键给）。 */
    long nextEventSeq();

    /** 把 mutator 里追加进 events 的那几条写下去（update 内部已处理，这里只给需要单写的场景用）。 */
    void appendEvent(WorkOrder o, OrderEventLog log);

    /** 单号：BX + 当天日期 8 位 + 3 位序号。 */
    String nextNo();

    /** 清空（演示重置用）。 */
    void clear();

    int size();

    record Page(List<WorkOrder> items, int total, int page, int pageSize) {
    }
}
