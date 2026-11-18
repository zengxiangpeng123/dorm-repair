package com.zxp.dormrepair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.zxp.dormrepair.auth.InMemoryUserRepository;
import com.zxp.dormrepair.auth.PasswordHasher;
import com.zxp.dormrepair.auth.UserAccount;
import com.zxp.dormrepair.domain.Actor;
import com.zxp.dormrepair.dto.SubmitRequest;
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

@SpringBootTest(properties = {
        "app.order.auto-assign=true",
        "app.ai.base-url=http://127.0.0.1:9",
        "app.file.allow-legacy-demo-urls=true"
})
class AutoAssignWithoutWorkerTest {

    @Autowired OrderService orders;
    @Autowired OrderRepository store;
    @Autowired InMemoryUserRepository users;

    private Actor student;

    @BeforeEach
    void setUp() {
        store.clear();
        users.clear();
        student = new Actor(ActorType.STUDENT, "S001", "张同学");
    }

    @Test
    @DisplayName("自动派工找不到师傅时仍建单成功并保留待派工")
    void keepsPendingWhenNoWorkerExists() {
        users.insertWorkerWithBuildings(new UserAccount("W0512", ActorType.WORKER,
                "王海涛", "综合班", "B8", null, null, true, PasswordHasher.demoHash()), List.of("B8"));

        var created = orders.submit(new SubmitRequest("B7", 4, "402",
                "插座打火，有焦味", "全天", List.of("demo://no-worker.jpg"),
                null, "电气", "紧急", null, null), student);

        var saved = store.require(created.no());
        assertEquals("PENDING", created.status());
        assertEquals(OrderStatus.PENDING, saved.getStatus());
        assertNull(saved.getWorkerId());
    }
}