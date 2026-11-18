package com.zxp.dormrepair.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zxp.dormrepair.store.entity.ExceptionRecordRow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ExceptionRecordMapper extends BaseMapper<ExceptionRecordRow> {

    @Delete("DELETE FROM t_exception_record")
    int wipe();
}
