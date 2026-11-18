package com.zxp.dormrepair.store;

import com.zxp.dormrepair.domain.ExceptionRecord;
import java.util.List;
import java.util.Optional;

/** 例外清单的持久化入口；同一工单同类例外只保留一条 OPEN 记录。 */
public interface ExceptionRecordRepository {

    Optional<ExceptionRecord> findOpen(String kind, String orderNo);

    ExceptionRecord createRepeatRework(String orderNo, String detail, String at);

    List<ExceptionRecord> openByKind(String kind);

    void clear();
}
