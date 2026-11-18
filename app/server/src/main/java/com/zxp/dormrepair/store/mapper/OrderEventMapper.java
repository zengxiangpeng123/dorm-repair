package com.zxp.dormrepair.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zxp.dormrepair.store.entity.OrderEventRow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

/** 事件表：只增不改。 */
@Mapper
public interface OrderEventMapper extends BaseMapper<OrderEventRow> {

    @Delete("DELETE FROM t_work_order_event")
    int wipe();
}
