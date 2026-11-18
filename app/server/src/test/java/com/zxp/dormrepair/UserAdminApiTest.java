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
