package com.zxp.dormrepair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zxp.dormrepair.common.BizException;
import com.zxp.dormrepair.domain.Actor;
import com.zxp.dormrepair.dto.SubmitRequest;
import com.zxp.dormrepair.dto.TransitRequest;
import com.zxp.dormrepair.service.OrderService;
import com.zxp.dormrepair.statemachine.ActorType;
import com.zxp.dormrepair.statemachine.OrderStatus;
import com.zxp.dormrepair.store.OrderRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"app.order.auto-assign=false", "app.ai.base-url=http://127.0.0.1:9"})
class WorkerAssignmentScopeTest {

    @Autowired OrderService orders;
    @Autowired OrderRepository store;

    private Actor student;

    @BeforeEach
    void setUp() {
        store.clear();
        student = new Actor(ActorType.STUDENT, "S001", "张同学");
    }

    @Test
    @DisplayName("手动派工不能把 B7 工单派给只服务 B8 的师傅")
    void rejectsWorkerOutsideBuildingScope() {
        var created = orders.submit(new SubmitRequest("B7", 4, "402",
                "洗手盆软管一直滴水，需要处理", "全天", List.of("demo://scope.jpg"),
                null, "给排水", "普通", null, null), student);

        BizException e = assertThrows(BizException.class, () -> orders.assign(created.no(),
                new TransitRequest(null, null, null, null, null, null,
                        null, null, null, "W0512", null), Actor.system()));

        assertEquals("WORKER_BUILDING_MISMATCH", e.extra().get("reason"));
        assertEquals(OrderStatus.PENDING, store.require(created.no()).getStatus());
    }
}
