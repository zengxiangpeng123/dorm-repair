package com.zxp.dormrepair.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zxp.dormrepair.domain.WorkOrder;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 工单表。全部字段一起写 + {@code WHERE version = ?}，这是规矩 C-05 的落地方式：
 * 版本对不上就一行都改不动，返回 0，上层抛 409。
 */
@Mapper
public interface WorkOrderMapper extends BaseMapper<WorkOrder> {

    @Update("""
            UPDATE t_work_order SET
              status        = #{o.status},
              version       = version + 1,
              building_id   = #{o.building},
              floor         = #{o.floor},
              room          = #{o.room},
              student_id    = #{o.studentId},
              student_name  = #{o.studentName},
              description   = #{o.description},
              visit_window  = #{o.visitWindow},
              category      = #{o.category},
              level         = #{o.level},
              level_source  = #{o.levelSource},
              ai_basis      = #{o.aiBasis},
              worker_id     = #{o.workerId},
              worker_name   = #{o.workerName},
              parts_name    = #{o.partsName},
              parts_amount  = #{o.partsAmount},
              parts_eta     = #{o.partsEta,typeHandler=com.zxp.dormrepair.store.DateTimeStringTypeHandler},
              assigned_at   = #{o.assignedAt,typeHandler=com.zxp.dormrepair.store.DateTimeStringTypeHandler},
              arrive_at     = #{o.arriveAt,typeHandler=com.zxp.dormrepair.store.DateTimeStringTypeHandler},
              finish_at     = #{o.finishAt,typeHandler=com.zxp.dormrepair.store.DateTimeStringTypeHandler},
              sign_at       = #{o.signAt,typeHandler=com.zxp.dormrepair.store.DateTimeStringTypeHandler},
              closed_at     = #{o.closedAt,typeHandler=com.zxp.dormrepair.store.DateTimeStringTypeHandler},
              cancelled_at  = #{o.cancelledAt,typeHandler=com.zxp.dormrepair.store.DateTimeStringTypeHandler},
              status_at     = #{o.statusAt,typeHandler=com.zxp.dormrepair.store.DateTimeStringTypeHandler}
            WHERE no = #{o.no} AND version = #{expectVersion}
            """)
    int updateCas(@Param("o") WorkOrder o, @Param("expectVersion") int expectVersion);

    @Delete("DELETE FROM t_work_order")
    int wipe();
}
