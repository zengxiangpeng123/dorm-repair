package com.zxp.dormrepair.store;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zxp.dormrepair.domain.ReworkReview;
import com.zxp.dormrepair.store.entity.ReworkReviewRow;
import com.zxp.dormrepair.store.mapper.ReworkReviewMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.store", havingValue = "mysql")
public class MybatisReworkReviewRepository implements ReworkReviewRepository {

    private final ReworkReviewMapper mapper;

    public MybatisReworkReviewRepository(ReworkReviewMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ReworkReview createRequest(String orderNo, String requestedBy, String reason, String at) {
        ReworkReviewRow row = new ReworkReviewRow();
        row.setOrderNo(orderNo);
        row.setAttemptNo(mapper.nextAttemptNo(orderNo));
        row.setReworkIndex(null);
        row.setStatus("PENDING");
        row.setRequestedBy(requestedBy);
        row.setRequestReason(reason);
        row.setRequestedAt(at);
        mapper.insert(row);
        return toDomain(row);
    }

    @Override
    public Optional<ReworkReview> findPending(String orderNo) {
        ReworkReviewRow row = mapper.selectOne(Wrappers.<ReworkReviewRow>query()
                .eq("order_no", orderNo).eq("status", "PENDING")
                .orderByDesc("id").last("LIMIT 1"));
        return Optional.ofNullable(row).map(this::toDomain);
    }

    @Override
    public Optional<ReworkReview> findLatest(String orderNo) {
        ReworkReviewRow row = mapper.selectOne(Wrappers.<ReworkReviewRow>query()
                .eq("order_no", orderNo).orderByDesc("id").last("LIMIT 1"));
        return Optional.ofNullable(row).map(this::toDomain);
    }

    @Override
    public List<ReworkReview> pending() {
        return mapper.selectList(Wrappers.<ReworkReviewRow>query()
                .eq("status", "PENDING").orderByAsc("requested_at"))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public ReworkReview decide(ReworkReview review, String status, Integer reworkIndex,
                               String decidedBy, String reason, String at) {
        ReworkReviewRow row = mapper.selectById(review.id());
        if (row == null) {
            throw new IllegalStateException("返修审查记录不存在：" + review.id());
        }
        row.setStatus(status);
        row.setReworkIndex(reworkIndex);
        row.setDecidedBy(decidedBy);
        row.setDecisionReason(reason);
        row.setDecidedAt(at);
        mapper.updateById(row);
        return toDomain(row);
    }

    @Override
    public boolean hasPending(String orderNo) {
        return findPending(orderNo).isPresent();
    }

    @Override
    public void clear() {
        mapper.wipe();
    }

    private ReworkReview toDomain(ReworkReviewRow r) {
        return new ReworkReview(r.getId() == null ? 0 : r.getId(), r.getOrderNo(),
                r.getAttemptNo() == null ? 0 : r.getAttemptNo(), r.getReworkIndex(),
                r.getStatus(), r.getRequestedBy(), r.getRequestReason(), r.getRequestedAt(),
                r.getDecidedBy(), r.getDecisionReason(), r.getDecidedAt());
    }
}