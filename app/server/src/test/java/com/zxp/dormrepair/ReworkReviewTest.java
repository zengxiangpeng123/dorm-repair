package com.zxp.dormrepair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxp.dormrepair.common.BizException;
import com.zxp.dormrepair.domain.Actor;
import com.zxp.dormrepair.domain.ReworkReview;
import com.zxp.dormrepair.domain.WorkOrder;
import com.zxp.dormrepair.dto.OrderView;
import com.zxp.dormrepair.dto.ReworkRequestCreate;
import com.zxp.dormrepair.dto.ReworkReviewRequest;
import com.zxp.dormrepair.dto.SubmitRequest;
import com.zxp.dormrepair.dto.TransitRequest;
import com.zxp.dormrepair.service.OrderService;
import com.zxp.dormrepair.service.QueryService;
import com.zxp.dormrepair.service.SeedService;
import com.zxp.dormrepair.service.TimerService;
import com.zxp.dormrepair.statemachine.ActorType;
import com.zxp.dormrepair.statemachine.OrderEvent;
import com.zxp.dormrepair.statemachine.OrderStatus;
import com.zxp.dormrepair.statemachine.StateMachine;
import com.zxp.dormrepair.statemachine.TransitionTable;
import com.zxp.dormrepair.store.ExceptionRecordRepository;
import com.zxp.dormrepair.store.OrderRepository;
import com.zxp.dormrepair.store.ReworkReviewRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ReworkReviewTest {

    @Autowired OrderService orders;
    @Autowired SeedService seeder;
    @Autowired OrderRepository store;
    @Autowired ReworkReviewRepository reviews;
    @Autowired TimerService timers;
    @Autowired QueryService queries;
    @Autowired ExceptionRecordRepository exceptionRecords;

    @BeforeEach
    void reset() {
        seeder.reset();
    }

    @Test
    @DisplayName("学生申请返修不改变状态，管理员批准后才生成 -R1")
    void approveCreatesRework() {
        WorkOrder original = verifyingOrder();
        Actor student = actor(ActorType.STUDENT, original.getStudentId(), original.getStudentName());
        Actor admin = actor(ActorType.ADMIN, "A001", "王主任");

        Map<String, Object> request = orders.requestRework(original.getNo(),
                new ReworkRequestCreate(original.getVersion(), "锁芯还是涩，拧着费劲"), student);
        ReworkReview pending = reviews.findPending(original.getNo()).orElseThrow();
        assertEquals("PENDING", pending.status());
        assertEquals(original.getVersion() + 1, store.require(original.getNo()).getVersion());
        assertEquals(OrderStatus.VERIFYING, store.require(original.getNo()).getStatus());
        assertTrue(store.require(original.getNo()).getEvents().stream()
                .anyMatch(e -> e.event() == OrderEvent.REWORK_REQUEST));

        WorkOrder waiting = store.require(original.getNo());
        BizException paused = assertThrows(BizException.class,
                () -> timers.fireOne(waiting.getNo(), OrderEvent.AUTO_ACCEPT));
        assertEquals("REWORK_REVIEW_PENDING", paused.extra().get("reason"));

        Map<String, Object> decided = orders.reviewRework(original.getNo(),
                new ReworkReviewRequest(waiting.getVersion(), "APPROVE", "完工照显示锁芯未复位"),
                admin);
        OrderView originView = (OrderView) decided.get("original");
        OrderView childView = (OrderView) decided.get("reworkOrder");
        assertEquals("REWORKING", originView.status());
        assertEquals("PENDING", childView.status());
        assertTrue(childView.no().endsWith("-R1"));
        assertEquals(1, childView.reworkIndex());
        assertEquals("APPROVED", reviews.findLatest(original.getNo()).orElseThrow().status());
        assertTrue(store.require(original.getNo()).getEvents().stream()
                .anyMatch(e -> e.event() == OrderEvent.REJECT && "ADMIN".equals(e.actorType())));
    }

    @Test
    @DisplayName("管理员驳回误报后状态不变，再次申请产生新的 attemptNo")
    void rejectKeepsVerifyingAndAllowsNewAttempt() {
        WorkOrder original = verifyingOrder();
        Actor student = actor(ActorType.STUDENT, original.getStudentId(), original.getStudentName());
        Actor admin = actor(ActorType.ADMIN, "A001", "王主任");

        orders.requestRework(original.getNo(),
                new ReworkRequestCreate(original.getVersion(), "看起来没修好"), student);
        WorkOrder waiting = store.require(original.getNo());
        Map<String, Object> decided = orders.reviewRework(original.getNo(),
                new ReworkReviewRequest(waiting.getVersion(), "REJECT", "设备原本正常工作，属于学生误报"),
                admin);
        assertNull(decided.get("reworkOrder"));
        assertEquals(OrderStatus.VERIFYING, store.require(original.getNo()).getStatus());
        assertEquals("REJECTED", reviews.findLatest(original.getNo()).orElseThrow().status());
        assertNotNull(store.require(original.getNo()).getVerifyDueAt());

        WorkOrder afterReject = store.require(original.getNo());
        orders.requestRework(original.getNo(),
                new ReworkRequestCreate(afterReject.getVersion(), "第二次确认真实存在问题"), student);
        assertEquals(2, reviews.findPending(original.getNo()).orElseThrow().attemptNo());
    }

    @Test
    @DisplayName("第 2 次返修仍先审查，写入例外，验收后整条返修链关闭")
    void secondReworkIsReviewedAndClosesWholeChain() {
        WorkOrder original = verifyingOrder();
        Actor student = actor(ActorType.STUDENT, original.getStudentId(), original.getStudentName());
        Actor admin = actor(ActorType.ADMIN, "A001", "王主任");
        Actor worker = actor(ActorType.WORKER, "W0428", "李建国");

        orders.requestRework(original.getNo(),
                new ReworkRequestCreate(original.getVersion(), "第一次还是没修好"), student);
        WorkOrder waiting = store.require(original.getNo());
        @SuppressWarnings("unchecked")
        Map<String, Object> first = orders.reviewRework(original.getNo(),
                new ReworkReviewRequest(waiting.getVersion(), "APPROVE", "确认属于维修质量问题"),
                admin);
        OrderView r1 = (OrderView) first.get("reworkOrder");
        orders.assign(r1.no(), transit(r1.version()), Actor.system());
        WorkOrder r1Assigned = store.require(r1.no());
        orders.arrive(r1.no(), transit(r1Assigned.getVersion()), worker);
        WorkOrder r1Arrived = store.require(r1.no());
        orders.finish(r1.no(), finish(r1Arrived.getVersion(), "第二次维修仍未解决"), worker);
        WorkOrder r1Verifying = store.require(r1.no());
        assertEquals(OrderStatus.VERIFYING, r1Verifying.getStatus());

        orders.requestRework(r1.no(),
                new ReworkRequestCreate(r1Verifying.getVersion(), "还是漏水，需要再返修"), student);
        WorkOrder r1Waiting = store.require(r1.no());
        Map<String, Object> second = orders.reviewRework(r1.no(),
                new ReworkReviewRequest(r1Waiting.getVersion(), "APPROVE", "复核后仍是维修质量问题，转反复返修"),
                admin);
        OrderView r2 = (OrderView) second.get("reworkOrder");
        assertEquals(original.getNo() + "-R2", r2.no());
        assertEquals(2, r2.reworkIndex());
        assertEquals(OrderStatus.REWORKING, store.require(r1.no()).getStatus());
        assertEquals(2, store.require(original.getNo()).getReworkCount());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> exceptions =
                (List<Map<String, Object>>) queries.exceptions().get("items");
        assertTrue(exceptions.stream().anyMatch(e -> "REPEAT_REWORK".equals(e.get("kind"))
                && original.getNo().equals(e.get("no"))));
        assertTrue(exceptionRecords.findOpen("REPEAT_REWORK", original.getNo()).isPresent());

        orders.assign(r2.no(), transit(r2.version()), Actor.system());
        WorkOrder r2Assigned = store.require(r2.no());
        orders.arrive(r2.no(), transit(r2Assigned.getVersion()), worker);
        WorkOrder r2Arrived = store.require(r2.no());
        orders.finish(r2.no(), finish(r2Arrived.getVersion(), "最终修复完成"), worker);
        WorkOrder r2Verifying = store.require(r2.no());
        orders.accept(r2.no(), new TransitRequest(r2Verifying.getVersion(), null, null,
                "张同学", null, null, null, null, null, null, null), student);

        assertEquals(OrderStatus.DONE, store.require(r2.no()).getStatus());
        assertEquals(OrderStatus.CLOSED, store.require(original.getNo()).getStatus());
        assertEquals(OrderStatus.CLOSED, store.require(r1.no()).getStatus());
    }

    @Test
    @DisplayName("返修日志事件不进入 14 条转移矩阵，TRIGGER 正确")
    void auditEventsStayOutsideMatrix() {
        assertEquals(Set.of(ActorType.STUDENT), TransitionTable.triggerOf(OrderEvent.REWORK_REQUEST));
        assertEquals(Set.of(ActorType.ADMIN), TransitionTable.triggerOf(OrderEvent.REWORK_REJECTED));
        assertTrue(TransitionTable.triggerOf(OrderEvent.REJECT).contains(ActorType.ADMIN));
        assertEquals(false, OrderEvent.REWORK_REQUEST.changesStatus());
        assertEquals(14, StateMachine.matrix().get("eventCount"));
        assertEquals(14, StateMachine.matrix().get("legal"));
        assertEquals(140, StateMachine.matrix().get("total"));
    }

    @Test
    @DisplayName("无可用楼栋师傅时返回 422，不硬编码 W0428")
    void noWorkerLeavesPending() {
        Actor student = actor(ActorType.STUDENT, "S001", "张同学");
        OrderView submitted = orders.submit(new SubmitRequest("B9", 1, "101",
                "水管一直滴水，需要维修", "全天", List.of("demo://b9.jpg"),
                null, null, null, null, null), student);
        BizException e = assertThrows(BizException.class,
                () -> orders.assign(submitted.no(), new TransitRequest(null, null, null, null, null,
                        null, null, null, null, null, null), Actor.system()));
        assertEquals("NO_WORKER_AVAILABLE", e.extra().get("reason"));
        assertEquals(OrderStatus.PENDING, store.require(submitted.no()).getStatus());
    }

    private TransitRequest transit(int version) {
        return new TransitRequest(version, null, null, null, null, null, null, null, null, null, null);
    }

    private TransitRequest finish(int version, String note) {
        return new TransitRequest(version, note, null, null, List.of("demo://after/second.jpg"),
                null, null, null, null, null, null);
    }

    private WorkOrder verifyingOrder() {
        return store.all().stream().filter(o -> o.getStatus() == OrderStatus.VERIFYING)
                .findFirst().orElseThrow();
    }

    private Actor actor(ActorType type, String id, String name) {
        return new Actor(type, id, name);
    }
}