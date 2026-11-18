package com.zxp.dormrepair.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zxp.dormrepair.store.entity.ReworkReviewRow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ReworkReviewMapper extends BaseMapper<ReworkReviewRow> {

    @Select("SELECT COALESCE(MAX(attempt_no),0)+1 FROM t_rework_review WHERE order_no=#{orderNo}")
    int nextAttemptNo(@Param("orderNo") String orderNo);

    @Delete("DELETE FROM t_rework_review")
    int wipe();
}