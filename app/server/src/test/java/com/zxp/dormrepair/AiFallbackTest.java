package com.zxp.dormrepair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxp.dormrepair.service.AiClient;
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
 * AI 不在的时候，建单必须照样成功 —— 这是"AI 挂了不能拦住学生"那条铁律的证明。
 *
 * <p>把 base-url 指到一个没人监听的端口，模拟 AI 服务没起。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.ai.base-url=http://127.0.0.1:9", "app.ai.timeout-ms=300"})
class AiFallbackTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    @LocalServerPort
    private int port;

    private String base;

    @BeforeEach
    void setUp() throws Exception {
        base = "http://localhost:" + port + "/api/v1";
        call("POST", "/demo/reset", null, "system", "system");
    }

    @Test
    @DisplayName("AI 连不上：建单仍 201，落到兜底档，安全类词仍然给紧急")
    void submitStillWorks() throws Exception {
        Resp r = call("POST", "/orders", """
                {"building":"B7","floor":4,"room":"402","desc":"插座打火，有焦味，不敢用了",
                 "window":"上午 08:00-12:00","photosBefore":["demo://b1.jpg"]}
                """, "student", "S001");
        assertEquals(201, r.status, "AI 挂了也必须能建单");
        JsonNode o = r.data();
        assertEquals("PENDING", o.get("status").asText());
        assertEquals("FALLBACK", o.get("levelSource").asText());
        assertEquals("紧急", o.get("level").asText(), "兜底也要认安全类词");
        assertTrue(o.get("aiBasis").asText().contains("兜底"), "依据里要写明这是兜底：" + o.get("aiBasis"));
        assertEquals("给排水", o.get("category").asText(), "没 AI 时类型给兜底档");
    }

    @Test
    @DisplayName("AI 连不上：普通描述落到较急，不是普通")
    void fallbackIsConservative() throws Exception {
        Resp r = call("POST", "/orders", """
                {"building":"B8","floor":2,"room":"205","desc":"床板有点响，翻个身就咯吱",
                 "window":"全天（08:00-22:00）","photosBefore":["demo://b2.jpg"]}
                """, "student", "S003");
        assertEquals(201, r.status);
        assertEquals("较急", r.data().get("level").asText(), "判不出来宁可升一档");
    }

    @Test
    @DisplayName("显式指定类型时不吃 AI，标 MANUAL")
    void manualCategoryWins() throws Exception {
        Resp r = call("POST", "/orders", """
                {"building":"B7","floor":3,"room":"317","desc":"灯不亮，晚上看不清",
                 "photosBefore":["demo://b3.jpg"],"category":"电气","level":"普通"}
                """, "student", "S001");
        assertEquals(201, r.status);
        assertEquals("电气", r.data().get("category").asText());
        assertEquals("MANUAL", r.data().get("levelSource").asText());
    }

    @Test
    @DisplayName("AI 服务在线但运行规则模式时，健康检查必须标记未就绪")
    void healthRejectsRuleOnlyMode() throws Exception {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", exchange -> {
            byte[] body = """
                    {"status":"UP","classifier":{"mode":"rules","llmEnabled":false,"model":"raccoon/sn-deepseek-v4-1-flash-1017"}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            AiClient client = new AiClient("http://127.0.0.1:" + server.getAddress().getPort(),
                    "raccoon/sn-deepseek-v4-1-flash-1017", "provider", 1000, 1000);
            java.util.Map<String, Object> health = client.health();
            assertEquals(Boolean.TRUE, health.get("up"));
            assertEquals(Boolean.FALSE, health.get("llmEnabled"));
            assertEquals(Boolean.FALSE, health.get("llmReady"));
            assertEquals("rules", health.get("mode"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("AI 服务模型与后端不一致时，健康检查必须标记未就绪")
    void healthRejectsModelMismatch() throws Exception {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", exchange -> {
            byte[] body = """
                    {"status":"UP","classifier":{"mode":"llm","llmEnabled":true,"model":"other-model"}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            AiClient client = new AiClient("http://127.0.0.1:" + server.getAddress().getPort(),
                    "raccoon/sn-deepseek-v4-1-flash-1017", "provider", 1000, 1000);
            java.util.Map<String, Object> health = client.health();
            assertEquals(Boolean.TRUE, health.get("up"));
            assertEquals(Boolean.TRUE, health.get("llmEnabled"));
            assertEquals(Boolean.FALSE, health.get("modelMatched"));
            assertEquals(Boolean.FALSE, health.get("llmReady"));
            assertEquals("other-model", health.get("remoteModel"));
        } finally {
            server.stop(0);
        }
    }
    @Test
    @DisplayName("AiClient.fallback 是纯函数：不联网也能验")
    void fallbackUnit() {
        AiClient c = new AiClient("http://127.0.0.1:9", "test-model", "test-provider", 200, 200);
        AiClient.Verdict water = c.fallback("洗手台下面漏水，柜子里全是水", "单测");
        assertEquals("紧急", water.level());
        assertTrue(water.fallback());
        AiClient.Verdict plain = c.fallback("桌子有点晃", "单测");
        assertEquals("较急", plain.level());
        assertEquals(0.0, plain.confidence(), 0.001);
        // health 也要能安全失败，不能抛
        assertFalse((Boolean) c.health().get("up"));
    }

    // ------------------------------------------------------------------

    private record Resp(int status, JsonNode body) {
        JsonNode data() {
            return body.get("data");
        }
    }

    private Resp call(String method, String path, String body, String actorType, String actorId)
            throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(15))
                .header("X-Actor-Type", actorType)
                .header("X-Actor-Id", actorId)
                .header("X-Actor-Name", actorType + "-" + actorId);
        if (body == null) {
            b.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            b.header("Content-Type", "application/json; charset=utf-8")
                    .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }
        HttpResponse<String> res = HTTP.send(b.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Resp(res.statusCode(), JSON.readTree(res.body()));
    }
}
