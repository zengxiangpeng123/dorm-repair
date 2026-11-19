package com.zxp.dormrepair;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.demo=false", "app.store=memory"})
class MetaHealthPrivacyTest {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    @LocalServerPort int port;

    @Test
    @DisplayName("公开字典接口可匿名读取，且不暴露工单数量")
    void publicMetadataDoesNotExposeOrderCounts() throws Exception {
        for (String path : java.util.List.of("/api/v1/meta", "/api/v1/state/matrix",
                "/api/v1/state/transitions")) {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<String> response = HTTP.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertTrue(response.statusCode() == 200, path + " -> " + response.statusCode());
            assertFalse(response.body().contains("\"orders\""), path + " -> " + response.body());
            assertFalse(response.body().contains("\"password\""), path + " -> " + response.body());
        }
    }

    @Test
    @DisplayName("生产健康接口不暴露工单数量")
    void productionHealthDoesNotExposeOrderCount() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/api/v1/health"))
                .timeout(Duration.ofSeconds(10)).GET().build();
        HttpResponse<String> response = HTTP.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertTrue(response.body().contains("\"status\":\"UP\""), response.body());
        assertFalse(response.body().contains("\"orders\""), response.body());
    }
}
