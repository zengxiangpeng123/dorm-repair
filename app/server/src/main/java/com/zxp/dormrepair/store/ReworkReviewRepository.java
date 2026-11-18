package com.zxp.dormrepair.store;

import com.zxp.dormrepair.domain.ReworkReview;
import java.util.List;
import java.util.Optional;

/** 返修审查仓储：每次申请独立一行。 */
public interface ReworkReviewRepository {

    ReworkReview createRequest(String orderNo, String requestedBy, String reason, String at);

    Optional<ReworkReview> findPending(String orderNo);

    Optional<ReworkReview> findLatest(String orderNo);

    List<ReworkReview> pending();

    ReworkReview decide(ReworkReview review, String status, Integer reworkIndex,
                        String decidedBy, String reason, String at);

    boolean hasPending(String orderNo);

    void clear();
}