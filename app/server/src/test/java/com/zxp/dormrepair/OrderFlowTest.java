package com.zxp.dormrepair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxp.dormrepair.common.BizException;
import com.zxp.dormrepair.domain.Actor;
import com.zxp.dormrepair.domain.IdempotencyRecord;
import com.zxp.dormrepair.domain.WorkOrder;
import com.zxp.dormrepair.statemachine.OrderStatus;
import com.zxp.dormrepair.store.OrderRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 黑盒接口测试：一条链走完 + 该拒的必须拒。
 *
 * <p>走真 HTTP，不用 mock —— 规矩 S-04：数据库、状态机都不能假。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.ai.base-url=http://127.0.0.1:9", "app.ai.timeout-ms=200"})
class OrderFlowTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    @LocalServerPort
    private int port;

    @Autowired OrderRepository store;

    private String base;

    @BeforeEach
    void setUp() throws Exception {
        base = "http://localhost:" + port + "/api/v1";
        call("POST", "/demo/reset", null, "system", "system");
    }

    @Test
    @DisplayName("一条链：待派工→已派工→维修中→待验收→已完成，版本一路加")
    void oneChain() throws Exception {
        Resp created = call("POST", "/orders", """
                {"building":"B7","floor":4,"room":"402",
                 "desc":"洗手盆软管一直滴水，水桶快接满了",
                 "window":"上午 08:00-12:00","photosBefore":["demo://b1.jpg"]}
                """, "student", "S001");
        assertEquals(201, created.status);
        JsonNode o = created.data();
        String no = o.get("no").asText();
        assertEquals("PENDING", o.get("status").asText());
        assertEquals(1, o.get("version").asInt());
        assertTrue(no.startsWith("BX"), "单号要以 BX 开头，实际 " + no);

        Resp assigned = call("POST", "/orders/" + no + "/assign",
                "{\"version\":1,\"workerId\":\"W0428\"}", "system", "system");
        assertEquals(200, assigned.status);
        assertEquals("ASSIGNED", assigned.data().get("status").asText());
        assertEquals("李建国", assigned.data().get("workerName").asText());

        Resp arrived = call("POST", "/orders/" + no + "/arrive", "{\"version\":2}", "worker", "W0428");
        assertEquals("REPAIRING", arrived.data().get("status").asText());
        assertTrue(arrived.data().get("arriveAt").isTextual(), "到门要写 arriveAt");

        Resp finished = call("POST", "/orders/" + no + "/finish",
                "{\"version\":3,\"photosAfter\":[\"demo://a1.jpg\"],\"note\":\"更换软管\"}",
                "worker", "W0428");
        assertEquals("VERIFYING", finished.data().get("status").asText());
        assertTrue(finished.data().get("finishAt").isTextual(), "完工要写 finishAt");

        Resp accepted = call("POST", "/orders/" + no + "/accept",
                "{\"version\":4,\"signerName\":\"张同学\"}", "student", "S001");
        assertEquals("DONE", accepted.data().get("status").asText());
        assertTrue(accepted.data().get("signAt").isTextual(), "验收要写 signAt");
        assertEquals(5, accepted.data().get("version").asInt());
    }

    @Test
    @DisplayName("旧 reject 已停用，必须走申请与管理员审查")
    void reworkAndCascade() throws Exception {
        String no = pending().get("no").asText();
        assign(no, 1);
        call("POST", "/orders/" + no + "/arrive", "{\"version\":2}", "worker", "W0428");
        call("POST", "/orders/" + no + "/finish",
                "{\"version\":3,\"photosAfter\":[\"demo://a1.jpg\"]}", "worker", "W0428");

        JsonNode before = call("GET", "/orders/" + no, null, null, null).data();
        String originLevel = before.get("level").asText();

        Resp rejected = call("POST", "/orders/" + no + "/reject",
                "{\"version\":4,\"reason\":\"水还在滴\"}", "student", "S001");
        assertEquals(410, rejected.status, rejected.body);
        assertEquals("LEGACY_REJECT_DISABLED", rejected.error().get("code").asText());
        assertEquals("VERIFYING",
                call("GET", "/orders/" + no, null, null, null).data().get("status").asText());

        Resp requested = call("POST", "/orders/" + no + "/rework-request",
                "{\"version\":4,\"reason\":\"水还在滴\"}", "student", "S001");
        assertEquals(202, requested.status, requested.body);
        JsonNode waiting = call("GET", "/orders/" + no, null, null, null).data();
        Resp approved = call("POST", "/orders/" + no + "/rework-review",
                "{\"version\":" + waiting.get("version").asInt()
                        + ",\"decision\":\"APPROVE\",\"reason\":\"完工照显示接头未紧固\"}",
                "admin", "A001");
        assertEquals(200, approved.status, approved.body);
        JsonNode rw = approved.data().get("reworkOrder");
        assertEquals(no + "-R1", rw.get("no").asText(), "返修单号是原单号加 -R1");
        assertEquals("PENDING", rw.get("status").asText(), "返修单回待派工重新排队");
        assertEquals(no, rw.get("reworkOf").asText());
        assertEquals(1, rw.get("reworkIndex").asInt());
        assertEquals(before.get("building").asText(), rw.get("building").asText(), "位置继承");
        assertEquals(before.get("room").asText(), rw.get("room").asText(), "房间继承");
        assertEquals(before.get("category").asText(), rw.get("category").asText(), "类型继承");
        if ("紧急".equals(originLevel)) {
            // 原单已经是最高档（这张单命中浸水风险），返修不再往上加
            assertEquals("紧急", rw.get("level").asText(), "已经最紧急就停在紧急");
        } else {
            assertNotEquals(originLevel, rw.get("level").asText(), "紧急度要升一档");
        }

        JsonNode origin = call("GET", "/orders/" + no, null, null, null).data();
        assertEquals("REWORKING", origin.get("status").asText(), "原单挂在返修中");
        assertEquals(1, origin.get("reworkCount").asInt(), "原单累计返修次数 +1");

        // 返修单走完到验收通过，原单应被级联关闭
        String rno = rw.get("no").asText();
        call("POST", "/orders/" + rno + "/assign", "{\"version\":1,\"workerId\":\"W0428\"}", "system", "system");
        call("POST", "/orders/" + rno + "/arrive", "{\"version\":2}", "worker", "W0428");
        call("POST", "/orders/" + rno + "/finish",
                "{\"version\":3,\"photosAfter\":[\"demo://a2.jpg\"]}", "worker", "W0428");
        Resp ok = call("POST", "/orders/" + rno + "/accept",
                "{\"version\":4,\"signerName\":\"张同学\"}", "student", "S001");
        assertEquals("DONE", ok.data().get("status").asText());

        JsonNode closed = call("GET", "/orders/" + no, null, null, null).data();
        assertEquals("CLOSED", closed.get("status").asText(), "返修单验收通过，原单关闭");
        assertTrue(closed.get("closedAt").isTextual());
        boolean cascaded = false;
        for (JsonNode e : closed.get("events")) {
            if ("ORIGIN_CLOSED".equals(e.get("event").asText())) {
                cascaded = true;
                assertEquals(1, e.get("manual").asInt(), "级联关闭是人验收导致的，不算系统自动化");
            }
        }
        assertTrue(cascaded, "原单要留下一条 ORIGIN_CLOSED 事件");
    }

    @Test
    @DisplayName("非法转移 409，且工单一个字没动")
    void illegalTransition() throws Exception {
        String no = pending().get("no").asText();
        assign(no, 1);
        call("POST", "/orders/" + no + "/arrive", "{\"version\":2}", "worker", "W0428");
        call("POST", "/orders/" + no + "/finish",
                "{\"version\":3,\"photosAfter\":[\"demo://a1.jpg\"]}", "worker", "W0428");

        // 待验收状态下再推完工：状态机里没有这条边
        Resp again = call("POST", "/orders/" + no + "/finish", "{\"version\":4}", "worker", "W0428");
        assertEquals(409, again.status);
        assertEquals("STATE_CONFLICT", again.error().get("code").asText());
        assertEquals("VERIFYING", again.error().get("from").asText());
        assertTrue(again.error().get("allowed").isArray());

        JsonNode after = call("GET", "/orders/" + no, null, null, null).data();
        assertEquals("VERIFYING", after.get("status").asText());
        assertEquals(4, after.get("version").asInt(), "被拒之后版本也不许动");
    }

    @Test
    @DisplayName("旧版本号 409 VERSION_CONFLICT，带两个版本号")
    void versionConflict() throws Exception {
        String no = pending().get("no").asText();
        assign(no, 1);
        Resp stale = call("POST", "/orders/" + no + "/arrive", "{\"version\":1}", "worker", "W0428");
        assertEquals(409, stale.status);
        assertEquals("VERSION_CONFLICT", stale.error().get("reason").asText());
        assertEquals(1, stale.error().get("expectVersion").asInt());
        assertEquals(2, stale.error().get("gotVersion").asInt());
    }

    @Test
    @DisplayName("前置不满足给 422，不是 409：完工没照片")
    void precondition() throws Exception {
        String no = pending().get("no").asText();
        assign(no, 1);
        call("POST", "/orders/" + no + "/arrive", "{\"version\":2}", "worker", "W0428");
        Resp noPhoto = call("POST", "/orders/" + no + "/finish", "{\"version\":3}", "worker", "W0428");
        assertEquals(422, noPhoto.status);
        assertEquals("PHOTO_AFTER_REQUIRED", noPhoto.error().get("reason").asText());
        assertEquals("REPAIRING", call("GET", "/orders/" + no, null, null, null)
                .data().get("status").asText());

        Resp noDesc = call("POST", "/orders",
                "{\"photosBefore\":[\"demo://x.jpg\"],\"desc\":\"水\"}", "student", "S001");
        assertEquals(422, noDesc.status);
        assertEquals("DESC_TOO_SHORT", noDesc.error().get("reason").asText());

        Resp noPhotoBefore = call("POST", "/orders", "{\"desc\":\"管子漏水了\"}", "student", "S001");
        assertEquals(422, noPhotoBefore.status);
    }

    @Test
    @DisplayName("撤销边界：没进楼能撤，进了楼一律拒")
    void cancelBoundary() throws Exception {
        String a = pending().get("no").asText();
        Resp ok = call("POST", "/orders/" + a + "/cancel", "{\"version\":1,\"reason\":\"不需要了\"}",
                "student", "S001");
        assertEquals(200, ok.status);
        assertEquals("CANCELLED", ok.data().get("status").asText());

        String b = pending().get("no").asText();
        assign(b, 1);
        call("POST", "/orders/" + b + "/arrive", "{\"version\":2}", "worker", "W0428");
        Resp denied = call("POST", "/orders/" + b + "/cancel", "{\"version\":3}", "student", "S001");
        assertEquals(409, denied.status);
        assertEquals("REPAIRING", denied.error().get("from").asText());

        Resp apply = call("POST", "/orders/" + b + "/cancel-request",
                "{\"version\":3,\"reason\":\"我等不了\"}", "student", "S001");
        assertEquals(202, apply.status);
        assertEquals("REPAIRING", apply.data().get("status").asText(), "申请撤销不改状态");

        JsonNode cr = apply.data().get("cancelRequest");
        assertNotNull(cr, "申请撤销要能在工单视图里看见，否则师傅照样白跑");
        assertEquals("我等不了", cr.get("reason").asText());
        assertEquals("REPAIRING", call("GET", "/orders/" + b, null, "worker", "W0428")
                .data().get("status").asText(), "重新查一遍也还是维修中");
    }

    @Test
    @DisplayName("管理员不能调用普通 cancel 绕过撤销申请")
    void adminCannotUseStudentCancelEndpoint() throws Exception {
        String no = pending().get("no").asText();
        Resp denied = call("POST", "/orders/" + no + "/cancel",
                "{\"version\":1,\"reason\":\"管理员直接撤销\"}", "admin", "A001");
        assertEquals(403, denied.status, denied.body);
        assertEquals("PENDING", call("GET", "/orders/" + no, null, null, null)
                .data().get("status").asText());
    }

    @Test
    @DisplayName("到访未遇原因必须来自枚举，非法原因不改变工单")
    void noShowRejectsUnknownReason() throws Exception {
        String no = pending().get("no").asText();
        assign(no, 1);
        Resp invalid = call("POST", "/orders/" + no + "/no-show",
                "{\"version\":2,\"reason\":\"随便写的理由\"}", "worker", "W0428");

        assertEquals(422, invalid.status, invalid.body);
        assertEquals("NO_SHOW_REASON_INVALID", invalid.error().get("reason").asText());
        JsonNode current = call("GET", "/orders/" + no, null, "admin", "A001").data();
        assertEquals("ASSIGNED", current.get("status").asText());
    }

    @Test
    @DisplayName("楼栋批量派工允许部分成功，已派单进入 skipped")
    void buildingAssignReturnsPerOrderResults() throws Exception {
        JsonNode list = call("GET", "/orders?status=PENDING&page=1&pageSize=200",
                null, "admin", "A001").data();
        java.util.Set<String> pending = new java.util.HashSet<>();
        for (JsonNode item : list.get("items")) pending.add(item.get("no").asText());
        assertTrue(pending.size() >= 2, "seed 中应至少有两张待派工单");

        JsonNode result = call("POST", "/buildings/B7/assign",
                "{\"workerId\":\"W0428\"}", "admin", "A001").data();

        assertTrue(result.get("ok").size() > 0, result.toString());
        for (JsonNode no : result.get("ok")) {
            assertTrue(pending.contains(no.asText()), "批量派工不应处理非待派工单：" + no);
        }
        JsonNode again = call("POST", "/buildings/B7/assign",
                "{\"workerId\":\"W0428\"}", "admin", "A001").data();
        assertTrue(again.get("skipped").size() > 0, again.toString());
    }

    @Test
    @DisplayName("事件流按 page/pageSize 分页并返回真实总数")
    void eventStreamPaginates() throws Exception {
        JsonNode list = call("GET", "/orders?page=1&pageSize=200", null, "admin", "A001").data();
        String no = null;
        for (JsonNode item : list.get("items")) {
            if (item.get("events").size() >= 2) {
                no = item.get("no").asText();
                break;
            }
        }
        assertNotNull(no, "seed 中应至少有两条事件的工单");
        JsonNode page = call("GET", "/orders/" + no + "/events?page=2&pageSize=2",
                null, "admin", "A001").data();

        assertTrue(page.get("total").asInt() >= 2, page.toString());
        assertTrue(page.get("items").size() <= 2, page.toString());
        assertEquals(2, page.get("page").asInt());
        assertEquals(2, page.get("pageSize").asInt());
    }

    @Test
    @DisplayName("管理端工单列表按 page/pageSize 分页并返回总数")
    void adminListPaginates() throws Exception {
        JsonNode first = call("GET", "/orders?page=1&pageSize=2", null, "admin", "A001").data();
        assertEquals(2, first.get("items").size());
        assertEquals(2, first.get("pageSize").asInt());
        assertTrue(first.get("total").asInt() >= 2);
        assertTrue(first.get("hasMore").asBoolean());

        JsonNode second = call("GET", "/orders?page=2&pageSize=2", null, "admin", "A001").data();
        assertNotEquals(first.get("items").get(0).get("no").asText(),
                second.get("items").get(0).get("no").asText());
    }

    @Test
    @DisplayName("管理端状态过滤与分页组合不改变 ACTIVE/DONE 口径")
    void adminListFiltersAndPaginates() throws Exception {
        JsonNode active = call("GET", "/orders?status=ACTIVE&page=1&pageSize=2", null, "admin", "A001").data();
        assertEquals(2, active.get("items").size());
        for (JsonNode item : active.get("items")) {
            assertTrue(java.util.Set.of("PENDING", "ASSIGNED", "REPAIRING", "PARTS", "VERIFYING", "REWORKING")
                    .contains(item.get("status").asText()));
        }
        JsonNode done = call("GET", "/orders?status=DONE&page=1&pageSize=2", null, "admin", "A001").data();
        for (JsonNode item : done.get("items")) {
            assertTrue(java.util.Set.of("DONE", "CLOSED", "CANCELLED").contains(item.get("status").asText()));
        }
    }
    @Test
    @DisplayName("管理端分页参数有上限，pageSize 最大 200")
    void adminListCapsPageSize() throws Exception {
        JsonNode data = call("GET", "/orders?page=1&pageSize=10000", null, "admin", "A001").data();
        assertEquals(200, data.get("pageSize").asInt());
    }

    @Test
    @DisplayName("身份不对给 403，不是 409")
    void forbidden() throws Exception {
        String no = pending().get("no").asText();
        Resp studentAssign = call("POST", "/orders/" + no + "/assign", "{\"version\":1}",
                "student", "S001");
        assertEquals(403, studentAssign.status);

        assign(no, 1);
        Resp otherWorker = call("POST", "/orders/" + no + "/arrive", "{\"version\":2}",
                "worker", "W0512");
        assertEquals(403, otherWorker.status);
        assertEquals("NOT_OWNER", otherWorker.error().get("code").asText());
        Resp otherFinish = call("POST", "/orders/" + no + "/finish",
                "{\"version\":2,\"photosAfter\":[\"demo://x.jpg\"]}", "worker", "W0512");
        assertEquals(403, otherFinish.status);
        assertEquals("NOT_OWNER", otherFinish.error().get("code").asText());

        Resp otherParts = call("POST", "/orders/" + no + "/need-parts",
                "{\"version\":2,\"partsName\":\"阀芯\",\"partsAmount\":38,\"partsEta\":\"2026-11-20\"}",
                "worker", "W0512");
        assertEquals(403, otherParts.status);
        assertEquals("NOT_OWNER", otherParts.error().get("code").asText());

        Resp otherAccept = call("POST", "/orders/" + no + "/accept",
                "{\"version\":2,\"signerName\":\"别人\"}", "student", "S002");
        assertEquals(403, otherAccept.status);
        assertEquals("NOT_OWNER", otherAccept.error().get("code").asText());

        Resp otherCancel = call("POST", "/orders/" + no + "/cancel",
                "{\"version\":2,\"reason\":\"误操作\"}", "student", "S002");
        assertEquals(403, otherCancel.status);
        assertEquals("NOT_OWNER", otherCancel.error().get("code").asText());
    }

    @Test
    @DisplayName("矩阵 140 种组合有 14 条合法；不存在的单 404")
    void matrixAndNotFound() throws Exception {
        JsonNode m = call("GET", "/state/matrix", null, null, null).data();
        assertEquals(10, m.get("rowCount").asInt());
        assertEquals(140, m.get("total").asInt());
        assertEquals(14, m.get("legal").asInt());
        assertEquals(404, call("GET", "/orders/BX99999999999", null, null, null).status);
    }

    @Test
    @DisplayName("两颗表没到点谁也推不动：422；管理端作废仍是 501（变更单 CR-001）")
    void timersAreTimeDriven() throws Exception {
        String no = pending().get("no").asText();
        assign(no, 1);
        call("POST", "/orders/" + no + "/arrive", "{\"version\":2}", "worker", "W0428");
        call("POST", "/orders/" + no + "/finish", """
                {"version":3,"photosAfter":["demo://a.jpg"],"noteTags":["更换软管"]}
                """, "worker", "W0428");

        // 刚完工就来推自动验收：不允许，422
        Resp early = call("POST", "/orders/" + no + "/auto-accept", "{}", "system", "system");
        assertEquals(422, early.status, early.body);
        assertEquals("T1_NOT_DUE", early.error().get("reason").asText());

        // 归档同理：单子还没完成，状态机先拦（409），不是 422
        Resp earlyArchive = call("POST", "/orders/" + no + "/archive", "{}", "system", "system");
        assertEquals(409, earlyArchive.status, earlyArchive.body);

        // 学生没有资格推系统事件
        Resp byStudent = call("POST", "/orders/" + no + "/auto-accept", "{}", "student", "S001");
        assertEquals(403, byStudent.status, byStudent.body);

        // 非维修中工单没有撤销申请，管理员不能直接关闭
        Resp adminCancel = call("POST", "/orders/" + no + "/admin-cancel",
                "{\"version\":4,\"reason\":\"学生已确认不需要维修\"}", "admin", "A001");
        assertEquals(422, adminCancel.status, adminCancel.body);
        assertEquals("ADMIN_CANCEL_ONLY_REPAIRING", adminCancel.error().get("reason").asText());

        // 推完之后状态一个字没动
        Resp after = call("GET", "/orders/" + no, null, null, null);
        assertEquals("VERIFYING", after.data().get("status").asText());
    }

    @Test
    @DisplayName("管理员可批准维修中撤销申请，状态走 CANCEL 边并保留决定原因")
    void adminCanApproveCancelRequest() throws Exception {
        String no = pending().get("no").asText();
        assign(no, 1);
        call("POST", "/orders/" + no + "/arrive", "{\"version\":2}", "worker", "W0428");
        Resp requested = call("POST", "/orders/" + no + "/cancel-request",
                "{\"version\":3,\"reason\":\"师傅排队太久，我不修了\"}", "student", "S001");
        assertEquals(202, requested.status, requested.body);
        Resp approved = call("POST", "/orders/" + no + "/admin-cancel",
                "{\"version\":4,\"reason\":\"确认学生不再维修，停止派工\"}", "admin", "A001");
        assertEquals(200, approved.status, approved.body);
        assertEquals("CANCELLED", approved.data().get("status").asText());
        assertTrue(approved.data().get("events").toString().contains("管理员批准撤销"));
        assertTrue(approved.data().get("events").toString().contains("ADMIN_CANCEL"));
    }

    @Test
    @DisplayName("同一个 Idempotency-Key 重放首次建单结果，不重复生成工单")
    void idempotentSubmitReplaysFirstResponse() throws Exception {
        String body = """
                {"building":"B7","floor":4,"room":"402","desc":"门锁卡住，钥匙转不动",
                 "photosBefore":["demo://idem.jpg"]}
                """;
        Resp first = call("POST", "/orders", body, "student", "S001", "idem-submit-1");
        Resp second = call("POST", "/orders", body, "student", "S001", "idem-submit-1");
        assertEquals(201, first.status, first.body);
        assertEquals(201, second.status, second.body);
        assertEquals(first.data().get("no").asText(), second.data().get("no").asText());
    }

    @Test
    @DisplayName("并发同 key 只允许一个请求执行")
    void concurrentIdempotencyExecutesOnce() throws Exception {
        String body = """
                {"building":"B7","floor":4,"room":"402","desc":"柜门铰链松动，需要紧固",
                 "photosBefore":["demo://concurrent.jpg"]}
                """;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var gate = new java.util.concurrent.CountDownLatch(1);
            var a = pool.submit(() -> {
                gate.await();
                return call("POST", "/orders", body, "student", "S001", "idem-concurrent-1");
            });
            var b = pool.submit(() -> {
                gate.await();
                return call("POST", "/orders", body, "student", "S001", "idem-concurrent-1");
            });
            gate.countDown();
            Resp first = a.get(20, java.util.concurrent.TimeUnit.SECONDS);
            Resp second = b.get(20, java.util.concurrent.TimeUnit.SECONDS);
            boolean oneSucceeded = (first.status == 201) != (second.status == 201);
            boolean bothReplayedSameOrder = first.status == 201 && second.status == 201
                    && first.data().get("no").asText().equals(second.data().get("no").asText());
            assertTrue(oneSucceeded || bothReplayedSameOrder,
                    first.body + " / " + second.body);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("同一 Idempotency-Key 内容变化返回 409")
    void idempotencyRejectsChangedPayload() throws Exception {
        String firstBody = """
                {"building":"B7","floor":4,"room":"402","desc":"门锁卡住，钥匙转不动",
                 "photosBefore":["demo://idem-a.jpg"]}
                """;
        String changedBody = """
                {"building":"B7","floor":4,"room":"402","desc":"门锁卡住，钥匙断在里面",
                 "photosBefore":["demo://idem-b.jpg"]}
                """;
        assertEquals(201, call("POST", "/orders", firstBody, "student", "S001", "idem-submit-2").status);
        Resp changed = call("POST", "/orders", changedBody, "student", "S001", "idem-submit-2");
        assertEquals(409, changed.status, changed.body);
        assertEquals("IDEMPOTENCY_KEY_REUSED", changed.error().get("code").asText());
    }

    @Test
    @DisplayName("T1/T2 定时器只查询待验收和已完成工单")
    void timerCandidatesExcludeClosedOrders() {
        var statuses = store.findTimerCandidates().stream()
                .map(WorkOrder::getStatus).collect(java.util.stream.Collectors.toSet());
        assertTrue(statuses.stream().allMatch(s -> s == OrderStatus.VERIFYING || s == OrderStatus.DONE));
        assertTrue(statuses.contains(OrderStatus.VERIFYING));
    }

    @Test
    @DisplayName("未过期的幂等占位仍阻止重复执行")
    void freshIdempotencyClaimRejectsConcurrentRetry() {
        var repository = new com.zxp.dormrepair.store.InMemoryIdempotencyRepository();
        var service = new com.zxp.dormrepair.service.IdempotencyService(repository);
        Actor actor = new Actor(com.zxp.dormrepair.statemachine.ActorType.STUDENT, "S001", "张同学");
        var first = service.begin(actor, "POST", "/api/v1/orders", "fresh-key", "hash");
        assertTrue(first.owner() != null);
        BizException error = assertThrows(BizException.class,
                () -> service.begin(actor, "POST", "/api/v1/orders", "fresh-key", "hash"));
        assertEquals("IDEMPOTENCY_IN_PROGRESS", error.code());
    }

    @Test
    @DisplayName("崩溃遗留的过期幂等占位可被安全接管")
    void staleIdempotencyClaimCanBeTakenOver() {
        var repository = new com.zxp.dormrepair.store.InMemoryIdempotencyRepository();
        var service = new com.zxp.dormrepair.service.IdempotencyService(repository);
        Actor actor = new Actor(com.zxp.dormrepair.statemachine.ActorType.STUDENT, "S001", "张同学");
        var stale = new IdempotencyRecord("stale", actor.type().name(), actor.id(), "POST",
                "/api/v1/orders", "stale-key", "hash", "PROCESSING", null, null,
                "2020-01-01 00:00:00", null);
        assertTrue(repository.claim(stale, "2020-01-01 00:10:00"));
        var takeover = service.begin(actor, "POST", "/api/v1/orders", "stale-key", "hash");
        assertTrue(takeover.owner() != null);
    }

    // ------------------------------------------------------------- 小工具

    private JsonNode pending() throws Exception {
        Resp r = call("POST", "/orders", """
                {"building":"B7","floor":5,"room":"508",
                 "desc":"插座打火，有焦味","photosBefore":["demo://b2.jpg"]}
                """, "student", "S001");
        assertEquals(201, r.status);
        return r.data();
    }

    private void assign(String no, int version) throws Exception {
        Resp r = call("POST", "/orders/" + no + "/assign",
                "{\"version\":" + version + "}", "system", "system");
        assertEquals(200, r.status, r.body);
    }

    private record Resp(int status, String body) {
        JsonNode data() throws Exception {
            return JSON.readTree(body).get("data");
        }

        JsonNode error() throws Exception {
            return JSON.readTree(body).get("error");
        }
    }

    private Resp call(String method, String path, String body, String actorType, String actorId)
            throws Exception {
        return call(method, path, body, actorType, actorId, null);
    }

    private Resp call(String method, String path, String body, String actorType, String actorId,
                      String idempotencyKey)
            throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(15));
        // 鉴权上线后每个请求都得带身份；没写身份的只读调用默认按学生 S001 发（本类没有匿名用例）
        String type = actorType == null ? "student" : actorType;
        String id = actorId == null ? (actorType == null ? "S001" : actorId) : actorId;
        b.header("X-Actor-Type", type);
        if (idempotencyKey != null) b.header("Idempotency-Key", idempotencyKey);
        if (id != null) {
            b.header("X-Actor-Id", id);
            b.header("X-Actor-Name", type + "-" + id);
        }
        if (body == null) {
            b.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            b.header("Content-Type", "application/json; charset=utf-8");
            b.method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }
        HttpResponse<String> r = HTTP.send(b.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Resp(r.statusCode(), r.body());
    }
}
