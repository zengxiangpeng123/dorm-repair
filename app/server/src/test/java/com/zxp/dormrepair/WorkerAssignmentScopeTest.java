package com.zxp.dormrepair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxp.dormrepair.auth.InMemoryUserRepository;
import com.zxp.dormrepair.auth.PasswordHasher;
import com.zxp.dormrepair.auth.UserAccount;
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

@SpringBootTest(properties = {"app.order.auto-assign=false", "app.ai.base-url=http://127.0.0.1:9", "app.file.allow-legacy-demo-urls=true"})
class WorkerAssignmentScopeTest {

    @Autowired OrderService orders;
    @Autowired OrderRepository store;
    @Autowired InMemoryUserRepository users;

    private Actor student;

    @BeforeEach
    void setUp() {
        store.clear();
        student = new Actor(ActorType.STUDENT, "S001", "张同学");
    }

    @Test
    @DisplayName("自动派工按故障类别匹配班组，不把电气单派给综合班")
    void automaticAssignmentMatchesTeam() {
        String pw = PasswordHasher.demoHash();
        users.clear();
        users.insertWorkerWithBuildings(new UserAccount("W0428", ActorType.WORKER,
                "李建国", "水电班", "B7", null, null, true, pw), List.of("B7"));
        users.insertWorkerWithBuildings(new UserAccount("W0512", ActorType.WORKER,
                "王海涛", "综合班", "B7", null, null, true, pw), List.of("B7"));

        var created = orders.submit(new SubmitRequest("B7", 4, "402",
                "插座打火，有焦味", "全天", List.of("demo://team.jpg"),
                null, "电气", "紧急", null, null), student);
        var assigned = orders.assign(created.no(), new TransitRequest(null, null, null, null, null,
                null, null, null, null, null, null), Actor.system());

        assertEquals("W0428", assigned.workerId());
        assertEquals("ASSIGNED", assigned.status());
        assertTrue(store.require(created.no()).getEvents().stream()
                .anyMatch(e -> e.note().contains("按电气匹配水电班")));
    }

    @Test
    @DisplayName("本楼没有匹配班组时保留楼栋主责兜底并记录原因")
    void fallsBackToBuildingWorkerWhenTeamUnavailable() {
        String pw = PasswordHasher.demoHash();
        users.clear();
        users.insertWorkerWithBuildings(new UserAccount("W0512", ActorType.WORKER,
                "王海涛", "综合班", "B7", null, null, true, pw), List.of("B7"));

        var created = orders.submit(new SubmitRequest("B7", 4, "402",
                "插座打火，有焦味", "全天", List.of("demo://fallback.jpg"),
                null, "电气", "紧急", null, null), student);
        var assigned = orders.assign(created.no(), new TransitRequest(null, null, null, null, null,
                null, null, null, null, null, null), Actor.system());

        assertEquals("W0512", assigned.workerId());
        assertTrue(store.require(created.no()).getEvents().stream()
                .anyMatch(e -> e.note().contains("本楼栋暂无水电班，按楼栋主责兜底")));
    }
    @Test
    @DisplayName("手动派工不能把 B7 工单派给只服务 B8 的师傅")
    void rejectsWorkerOutsideBuildingScope() {
        String pw = PasswordHasher.demoHash();
        users.clear();
        users.insertWorkerWithBuildings(new UserAccount("W0428", ActorType.WORKER,
                "李建国", "水电班", "B7", null, null, true, pw), List.of("B7"));
        users.insertWorkerWithBuildings(new UserAccount("W0512", ActorType.WORKER,
                "王海涛", "综合班", "B8", null, null, true, pw), List.of("B8"));

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
