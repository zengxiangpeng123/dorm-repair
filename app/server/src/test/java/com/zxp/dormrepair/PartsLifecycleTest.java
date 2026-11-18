package com.zxp.dormrepair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.zxp.dormrepair.domain.Actor;
import com.zxp.dormrepair.dto.SubmitRequest;
import com.zxp.dormrepair.dto.TransitRequest;
import com.zxp.dormrepair.service.OrderService;
import com.zxp.dormrepair.statemachine.ActorType;
import com.zxp.dormrepair.statemachine.OrderStatus;
import com.zxp.dormrepair.store.OrderRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"app.order.auto-assign=false", "app.ai.base-url=http://127.0.0.1:9"})
class PartsLifecycleTest {

    @Autowired OrderService orders;
    @Autowired OrderRepository store;
    private Actor student;
    private Actor worker;

    @BeforeEach
    void setUp() {
        store.clear();
        student = new Actor(ActorType.STUDENT, "S001", "张同学");
        worker = new Actor(ActorType.WORKER, "W0428", "李建国");
    }

    @Test
    @DisplayName("配件到货后保留采购名称、金额和预计日期，便于归档前追溯")
    void keepsPartsFactsAfterArrival() {
        var created = orders.submit(new SubmitRequest("B7", 4, "402",
                "水龙头损坏，需要更换阀芯", "全天", List.of("demo://parts.jpg"),
                null, "给排水", "普通", null, null), student);
        orders.assign(created.no(), new TransitRequest(null, null, null, null, null, null,
                null, null, null, "W0428", null), Actor.system());
        var assigned = store.require(created.no());
        orders.arrive(created.no(), new TransitRequest(assigned.getVersion(), null, null,
                null, null, null, null, null, null, null, null), worker);
        var arrived = store.require(created.no());
        orders.needParts(created.no(), new TransitRequest(arrived.getVersion(), null, null,
                null, null, null, "阀芯", new BigDecimal("480.00"), "2026-11-20", null, null), worker);

        var parts = store.require(created.no());
        assertEquals(OrderStatus.PARTS, parts.getStatus());
        assertEquals("阀芯", parts.getPartsName());
        assertEquals(new BigDecimal("480.00"), parts.getPartsAmount());
        assertNotNull(parts.getPartsEta());

        orders.partsArrived(created.no(), new TransitRequest(parts.getVersion(), null, null,
                null, null, null, null, null, null, null, null), Actor.system());

        var requeued = store.require(created.no());
        assertEquals("阀芯", requeued.getPartsName());
        assertEquals(new BigDecimal("480.00"), requeued.getPartsAmount());
        assertNotNull(requeued.getPartsEta());
    }
}
