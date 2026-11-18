package com.zxp.dormrepair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxp.dormrepair.file.FileStorage;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** 证明 readiness 不只是进程存活：文件存储不可用时端点必须返回 DOWN。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.endpoint.health.group.readiness.include=readinessState,fileStorage")
class ReadinessTest {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    @LocalServerPort
    private int port;

    @MockitoBean
    private FileStorage storage;

    @Test
    @DisplayName("文件存储不可用时 readiness 返回 DOWN")
    void readinessFailsWhenFileStorageIsUnavailable() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalStateException("bucket unavailable"))
                .when(storage).checkHealth();
        org.mockito.Mockito.when(storage.type()).thenReturn("s3");


        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/actuator/health/readiness"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> response = HTTP.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(503, response.statusCode(), response.body());
        assertTrue(response.body().contains("\"status\":\"DOWN\""), response.body());
    }
}
