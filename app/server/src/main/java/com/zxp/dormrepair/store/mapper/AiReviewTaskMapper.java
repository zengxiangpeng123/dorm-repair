package com.zxp.dormrepair.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zxp.dormrepair.store.entity.AiReviewTaskRow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiReviewTaskMapper extends BaseMapper<AiReviewTaskRow> {
    @Delete("DELETE FROM t_ai_review_task")
    int wipe();
}
