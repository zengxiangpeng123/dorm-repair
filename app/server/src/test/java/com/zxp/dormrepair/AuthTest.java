package com.zxp.dormrepair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 真账号登录：口令换令牌，令牌换身份。
 *
 * <p>这里把 {@code app.auth.allow-header} 关掉 —— 也就是说 X-Actor-* 头在这套用例里完全无效，
 * 不登录就是进不来。别的用例还开着它是为了少写 8 处登录代码，不是因为线上能这么用。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.auth.allow-header=false", "app.demo=true"})
class AuthTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    @LocalServerPort
    private int port;

    private String base;

    @BeforeEach
    void setUp() throws Exception {
        base = "http://localhost:" + port + "/api/v1";
        // 每个用例都从七张干净的样本单开始（reset 需要管理端身份，这也是"登录之后才能动数据"本身）
        send("POST", "/demo/reset", "{}", login("A001"));
    }

    @Test
    @DisplayName("账号口令换令牌：拿到 token 和这个人是谁，口令绝不出现在响应里")
    void loginReturnsTokenAndUser() throws Exception {
        Resp r = send("POST", "/auth/login", "{\"account\":\"S001\",\"password\":\"dorm@2026\"}", null);
        assertEquals(200, r.status, r.body);
        JsonNode d = r.data();
        assertNotNull(d.get("token"));
        assertTrue(d.get("token").asText().length() >= 40);
        assertEquals("STUDENT", d.get("user").get("role").asText());
        assertEquals("张同学", d.get("user").get("name").asText());
        assertTrue(d.get("user").get("password") == null, "响应里不能带口令字段");
        assertTrue(d.get("ttlSeconds").asInt() > 0);
    }

    @Test
    @DisplayName("口令错 / 账号不存在，都是 401，措辞一样（不告诉攻击者哪个对）")
    void badCredentialsAreIndistinguishable() throws Exception {
        Resp wrongPw = send("POST", "/auth/login", "{\"account\":\"S001\",\"password\":\"nope\"}", null);
        Resp noUser = send("POST", "/auth/login", "{\"account\":\"S999\",\"password\":\"dorm@2026\"}", null);
        assertEquals(401, wrongPw.status);
        assertEquals(401, noUser.status);
        assertEquals(wrongPw.data().get("message").asText(), noUser.data().get("message").asText());
    }

    @Test
    @DisplayName("空账号空口令是 400，跟「填错了」分开")
    void emptyCredentialsAre400() throws Exception {
        assertEquals(400, send("POST", "/auth/login", "{\"account\":\"\",\"password\":\"\"}", null).status);
    }

    @Test
    @DisplayName("不带令牌：401；带了令牌：200。同一个接口，差别只在 Authorization 头")
    void tokenIsRequired() throws Exception {
        assertEquals(401, send("GET", "/my/orders?status=ACTIVE", null, null).status);
        String token = login("S001");
        Resp ok = send("GET", "/my/orders?status=ACTIVE", null, token);
        assertEquals(200, ok.status, ok.body);
        assertNotNull(ok.data().get("items"));
    }

    @Test
    @DisplayName("学生令牌看管理端看板是 403，不是 401 —— 认得你，但你没这个权限")
    void roleIsEnforced() throws Exception {
        String student = login("S001");
        Resp denied = send("GET", "/admin/dashboard", null, student);
        assertEquals(403, denied.status, denied.body);
        assertEquals("FORBIDDEN", denied.data().get("code").asText());
        String admin = login("A001");
        assertEquals(200, send("GET", "/admin/dashboard", null, admin).status);
    }

    @Test
    @DisplayName("学生只能看自己的工单，管理端可以查看全部")
    void orderDetailIsScopedToOwner() throws Exception {
        String student1 = login("S001");
        String student2 = login("S002");
        String admin = login("A001");
        JsonNode items = send("GET", "/orders", null, admin).data().get("items");
        String otherNo = null;
        for (JsonNode o : items) {
            if ("李同学".equals(o.get("studentName").asText())) {
                otherNo = o.get("no").asText();
                break;
            }
        }
        assertNotNull(otherNo, "seed 里应该有李同学的工单");
        assertEquals(403, send("GET", "/orders/" + otherNo, null, student1).status);
        assertEquals(200, send("GET", "/orders/" + otherNo, null, student2).status);
        assertEquals(200, send("GET", "/orders/" + otherNo, null, admin).status);
    }

    @Test
    @DisplayName("登出之后同一个令牌立刻作废")
    void logoutInvalidatesToken() throws Exception {
        String token = login("S002");
        assertEquals(200, send("GET", "/my/orders", null, token).status);
        assertEquals(200, send("POST", "/auth/logout", "{}", token).status);
        Resp after = send("GET", "/my/orders", null, token);
        assertEquals(401, after.status);
        assertEquals("UNAUTHORIZED", after.data().get("code").asText());
    }

    @Test
    @DisplayName("瞎编的令牌是 401，不会退化成本地默认学生")
    void forgedTokenRejected() throws Exception {
        Resp r = send("GET", "/my/orders", null, "abc.def.ghi");
        assertEquals(401, r.status);
        // 顺手证明关掉 allow-header 之后，冒名请求头彻底无效
        Resp headerOnly = sendRaw("GET", "/my/orders", null, "X-Actor-Type", "admin");
        assertEquals(401, headerOnly.status);
    }

    @Test
    @DisplayName("自检台拿 system 令牌推 AUTO_ACCEPT：身份是 system，但没到点照样 422")
    void systemTokenIsSystem() throws Exception {
        String admin = login("A001");
        Resp st = send("POST", "/auth/system-token", "{}", admin);
        assertEquals(200, st.status, st.body);
        String sys = st.data().get("token").asText();

        // 挑一张待验收的单（seed 里 005 是"完工照 0 张"那张，T1 还没到期）
        Resp list = send("GET", "/orders", null, admin);
        JsonNode items = list.data().get("items");
        String no = null;
        for (JsonNode o : items) {
            if ("VERIFYING".equals(o.get("status").asText())) {
                no = o.get("no").asText();
                break;
            }
        }
        assertNotNull(no, "seed 里应该有一张待验收的单");

        Resp push = send("POST", "/orders/" + no + "/auto-accept", "{}", sys);
        assertEquals(422, push.status, push.body);
        assertEquals("T1_NOT_DUE", push.data().get("reason").asText());

        // system 令牌只能由管理员申请，学生不能借演示接口给自己提权
        assertEquals(403, send("POST", "/auth/system-token", "{}", login("S001")).status);
    }

    private String login(String account) throws Exception {
        Resp r = send("POST", "/auth/login",
                "{\"account\":\"" + account + "\",\"password\":\"dorm@2026\"}", null);
        assertEquals(200, r.status, r.body);
        return r.data().get("token").asText();
    }

    private Resp send(String method, String path, String body, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(15));
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
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

    private Resp sendRaw(String method, String path, String body, String header, String value)
            throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(15))
                .header(header, value);
        b.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        HttpResponse<String> r = HTTP.send(b.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Resp(r.statusCode(), r.body());
    }

    /** 只取 {ok:false,error:{...}} 里的 error，省得每处都写 .path("error")。 */
    private record Resp(int status, String body) {
        JsonNode data() {
            JsonNode root = JSON.readTree(body);
            JsonNode err = root.get("error");
            return err != null ? err : root.get("data");
        }
    }
}
