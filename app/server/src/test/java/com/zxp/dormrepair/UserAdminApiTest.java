package com.zxp.dormrepair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Autowired;
import com.zxp.dormrepair.auth.InMemoryLoginAttemptRepository;
import org.springframework.test.annotation.DirtiesContext;
import com.zxp.dormrepair.statemachine.ActorType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.auth.allow-header=false", "app.demo=true"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class UserAdminApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    @LocalServerPort int port;
    @Autowired InMemoryLoginAttemptRepository loginAttempts;
    String base;

    @BeforeEach
    void setUp() throws Exception {
        base = "http://127.0.0.1:" + port + "/api/v1";
        loginAttempts.clearAll();
    }

    @Test
    @DisplayName("管理员可创建师傅并记录服务楼栋，响应不回显口令")
    void adminCreatesWorkerWithBuilding() throws Exception {
        String admin = login("A001", "dorm@2026");
        Resp created = send("POST", "/admin/users",
                "{\"id\":\"W9001\",\"role\":\"WORKER\",\"name\":\"测试师傅\",\"team\":\"综合班\",\"buildings\":[\"B7\"],\"password\":\"Strong-Worker-2026\"}",
                admin);
        assertEquals(200, created.status, created.body);
        assertFalse(created.body.contains("Strong-Worker-2026"));
        assertEquals("W9001", created.data().get("id").asText());

        Resp list = send("GET", "/admin/users", null, admin);
        assertEquals(200, list.status, list.body);
        assertTrue(list.body.contains("W9001"));
        assertFalse(list.body.contains("Strong-Worker-2026"));
        assertEquals(200, send("POST", "/auth/login",
                "{\"account\":\"W9001\",\"password\":\"Strong-Worker-2026\"}", null).status);
    }

    @Test
    @DisplayName("非管理员不能创建人员账号")
    void nonAdminCannotCreateUser() throws Exception {
        String student = login("S001", "dorm@2026");
        Resp r = send("POST", "/admin/users",
                "{\"id\":\"S9001\",\"role\":\"STUDENT\",\"name\":\"测试学生\",\"building\":\"B7\",\"room\":\"101\",\"password\":\"Strong-Student-2026\"}",
                student);
        assertEquals(403, r.status, r.body);
    }

    @Test
    @DisplayName("管理员重置口令后旧会话立即失效，新口令可登录")
    void passwordResetRevokesExistingSessions() throws Exception {
        String admin = login("A001", "dorm@2026");
        Resp created = send("POST", "/admin/users",
                "{\"id\":\"W9002\",\"role\":\"WORKER\",\"name\":\"重置测试师傅\",\"team\":\"综合班\",\"buildings\":[\"B7\"],\"password\":\"Old-Worker-2026\"}",
                admin);
        assertEquals(200, created.status, created.body);
        String oldToken = login("W9002", "Old-Worker-2026");

        Resp reset = send("POST", "/admin/users/W9002/password",
                "{\"password\":\"New-Worker-2026\"}", admin);
        assertEquals(200, reset.status, reset.body);
        assertTrue(reset.body.contains("revokedSessions"));

        Resp oldAccess = send("GET", "/my/orders", null, oldToken);
        assertEquals(401, oldAccess.status, oldAccess.body);
        assertEquals(200, send("POST", "/auth/login",
                "{\"account\":\"W9002\",\"password\":\"New-Worker-2026\"}", null).status);
    }

    @Test
    @DisplayName("停用师傅会撤销旧会话并阻止登录，恢复后可重新登录")
    void disableAndEnableAccount() throws Exception {
        String admin = login("A001", "dorm@2026");
        Resp created = send("POST", "/admin/users",
                "{\"id\":\"W9003\",\"role\":\"WORKER\",\"name\":\"停用测试师傅\",\"team\":\"综合班\",\"buildings\":[\"B7\"],\"password\":\"Active-Worker-2026\"}",
                admin);
        assertEquals(200, created.status, created.body);
        String worker = login("W9003", "Active-Worker-2026");

        Resp disabled = send("POST", "/admin/users/W9003/active", "{\"active\":false}", admin);
        assertEquals(200, disabled.status, disabled.body);
        assertFalse(disabled.data().get("active").asBoolean());
        assertTrue(disabled.data().get("revokedSessions").asInt() >= 1);

        assertEquals(401, send("GET", "/my/orders", null, worker).status);
        assertEquals(403, send("POST", "/auth/login",
                "{\"account\":\"W9003\",\"password\":\"Active-Worker-2026\"}", null).status);

        Resp enabled = send("POST", "/admin/users/W9003/active", "{\"active\":true}", admin);
        assertEquals(200, enabled.status, enabled.body);
        assertTrue(enabled.data().get("active").asBoolean());
        login("W9003", "Active-Worker-2026");
    }

    @Test
    @DisplayName("非管理员不能停用账号")
    void nonAdminCannotDisableAccount() throws Exception {
        String student = login("S001", "dorm@2026");
        Resp r = send("POST", "/admin/users/W0428/active", "{\"active\":false}", student);
        assertEquals(403, r.status, r.body);
    }

    @Test
    @DisplayName("管理员不能停用自己，也不能停用最后一个启用管理员")
    void cannotDisableSelfOrLastActiveAdmin() throws Exception {
        String admin = login("A001", "dorm@2026");
        Resp self = send("POST", "/admin/users/A001/active", "{\"active\":false}", admin);
        assertEquals(409, self.status, self.body);
        assertEquals("CANNOT_DISABLE_SELF", self.data().get("code").asText());

        var users = new com.zxp.dormrepair.auth.InMemoryUserRepository();
        users.clear();
        users.insert(new com.zxp.dormrepair.auth.UserAccount("A7001", ActorType.ADMIN,
                "唯一管理员", null, null, null, null, true,
                com.zxp.dormrepair.auth.PasswordHasher.demoHash()));
        var service = new com.zxp.dormrepair.service.UserAdminService(users,
                new com.zxp.dormrepair.auth.TokenStore(
                        new com.zxp.dormrepair.auth.InMemoryAuthSessionRepository(), users));
        var lastActive = org.junit.jupiter.api.Assertions.assertThrows(
                com.zxp.dormrepair.common.BizException.class,
                () -> service.setActive("A7001", false, "A9999"));
        assertEquals("LAST_ACTIVE_ADMIN", lastActive.code());
    }

    @Test
    @DisplayName("两个管理员并发互相停用时，系统至少保留一个启用管理员")
    void concurrentAdminDisableKeepsOneActive() throws Exception {
        var users = new com.zxp.dormrepair.auth.InMemoryUserRepository();
        users.clear();
        String hash = com.zxp.dormrepair.auth.PasswordHasher.demoHash();
        users.insert(new com.zxp.dormrepair.auth.UserAccount("A7001", ActorType.ADMIN,
                "管理员一", null, null, null, null, true, hash));
        users.insert(new com.zxp.dormrepair.auth.UserAccount("A7002", ActorType.ADMIN,
                "管理员二", null, null, null, null, true, hash));
        var service = new com.zxp.dormrepair.service.UserAdminService(users,
                new com.zxp.dormrepair.auth.TokenStore(
                        new com.zxp.dormrepair.auth.InMemoryAuthSessionRepository(), users));
        var start = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var one = pool.submit(() -> {
                start.await();
                try { service.setActive("A7001", false, "A7002"); return true; }
                catch (com.zxp.dormrepair.common.BizException e) { return false; }
            });
            var two = pool.submit(() -> {
                start.await();
                try { service.setActive("A7002", false, "A7001"); return true; }
                catch (com.zxp.dormrepair.common.BizException e) { return false; }
            });
            start.countDown();
            boolean first = one.get(10, java.util.concurrent.TimeUnit.SECONDS);
            boolean second = two.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertTrue(first ^ second, "并发互相停用必须恰好成功一个");
        } finally {
            pool.shutdownNow();
        }
        long activeAdmins = users.all().stream()
                .filter(u -> u.role() == ActorType.ADMIN && u.active()).count();
        assertEquals(1, activeAdmins);
    }

    @Test
    @DisplayName("active 必须是布尔值")
    void activeMustBeBoolean() throws Exception {
        String admin = login("A001", "dorm@2026");
        Resp r = send("POST", "/admin/users/W0428/active", "{\"active\":\"false\"}", admin);
        assertEquals(400, r.status, r.body);
    }

    private String login(String account, String password) throws Exception {
        Resp r = send("POST", "/auth/login",
                "{\"account\":\"" + account + "\",\"password\":\"" + password + "\"}", null);
        assertEquals(200, r.status, r.body);
        return r.data().get("token").asText();
    }

    private Resp send(String method, String path, String body, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(10));
        if (token != null) b.header("Authorization", "Bearer " + token);
        if (body == null) b.method(method, HttpRequest.BodyPublishers.noBody());
        else {
            b.header("Content-Type", "application/json; charset=utf-8");
            b.method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }
        HttpResponse<String> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Resp(r.statusCode(), r.body());
    }

    private record Resp(int status, String body) {
        JsonNode data() {
            JsonNode root = JSON.readTree(body);
            JsonNode err = root.get("error");
            return err != null ? err : root.get("data");
        }
    }
}
