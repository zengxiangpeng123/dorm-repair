package com.zxp.dormrepair.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zxp.dormrepair.store.entity.OrderPhotoRow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

/** 照片表：现场照 BEFORE / 完工照 AFTER。 */
@Mapper
public interface OrderPhotoMapper extends BaseMapper<OrderPhotoRow> {

    @Delete("DELETE FROM t_work_order_photo")
    int wipe();
}
