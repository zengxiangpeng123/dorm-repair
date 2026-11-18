package com.zxp.dormrepair.store;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zxp.dormrepair.domain.ExceptionRecord;
import com.zxp.dormrepair.store.entity.ExceptionRecordRow;
import com.zxp.dormrepair.store.mapper.ExceptionRecordMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.store", havingValue = "mysql")
public class MybatisExceptionRecordRepository implements ExceptionRecordRepository {

    private final ExceptionRecordMapper mapper;

    public MybatisExceptionRecordRepository(ExceptionRecordMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<ExceptionRecord> findOpen(String kind, String orderNo) {
        ExceptionRecordRow row = mapper.selectOne(Wrappers.<ExceptionRecordRow>query()
                .eq("kind", kind)
                .eq("order_no", orderNo)
                .eq("status", "OPEN")
                .orderByDesc("id")
                .last("LIMIT 1"));
        return Optional.ofNullable(row).map(this::toDomain);
    }

    @Override
    public ExceptionRecord createRepeatRework(String orderNo, String detail, String at) {
        ExceptionRecordRow row = new ExceptionRecordRow();
        row.setKind("REPEAT_REWORK");
        row.setOrderNo(orderNo);
        row.setDetail(detail);
        row.setStatus("OPEN");
        row.setCreatedAt(at);
        mapper.insert(row);
        return toDomain(row);
    }

    @Override
    public List<ExceptionRecord> openByKind(String kind) {
        return mapper.selectList(Wrappers.<ExceptionRecordRow>query()
                .eq("kind", kind)
                .eq("status", "OPEN")
                .orderByAsc("created_at"))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public void clear() {
        mapper.wipe();
    }

    private ExceptionRecord toDomain(ExceptionRecordRow r) {
        return new ExceptionRecord(r.getId() == null ? 0 : r.getId(), r.getKind(), r.getOrderNo(),
                r.getDetail(), r.getStatus(), r.getHandledBy(), r.getCreatedAt(), r.getClosedAt());
    }
}
