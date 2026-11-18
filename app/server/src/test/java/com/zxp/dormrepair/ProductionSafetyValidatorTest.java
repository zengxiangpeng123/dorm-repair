package com.zxp.dormrepair;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zxp.dormrepair.config.ProductionSafetyValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductionSafetyValidatorTest {

    private static ProductionSafetyValidator validator(String access, String secret, String session, String aiBaseUrl) {
        return validator(access, secret, session, aiBaseUrl, "https://s3.example.com");
    }

    private static ProductionSafetyValidator validator(String access, String secret, String session,
                                                        String aiBaseUrl, String s3Endpoint) {
        return new ProductionSafetyValidator(
                false, false, "jdbc:mysql://db/app?useSSL=true&verifyServerCertificate=true", "app", "strong-password",
                "s3", "dorm-files", s3Endpoint, access, secret, session, false,
                aiBaseUrl, "model-x");
    }

    @Test
    @DisplayName("生产禁止演示模式、身份头、默认口令和本机 AI")
    void rejectsUnsafeProductionConfiguration() {
        assertThrows(IllegalStateException.class, () -> new ProductionSafetyValidator(
                true, false, "jdbc:mysql://db/app?useSSL=true&verifyServerCertificate=true", "app", "strong-password", "s3", "dorm-files", "https://s3.example.com", "", "", "", false, "https://ai.example.com", "model-x"));
        assertThrows(IllegalStateException.class, () -> new ProductionSafetyValidator(
                false, true, "jdbc:mysql://db/app?useSSL=true&verifyServerCertificate=true", "app", "strong-password", "s3", "dorm-files", "https://s3.example.com", "", "", "", false, "https://ai.example.com", "model-x"));
        assertThrows(IllegalStateException.class, () -> new ProductionSafetyValidator(
                false, false, "jdbc:mysql://db/app?useSSL=true&verifyServerCertificate=true", "root", "123456", "s3", "dorm-files", "https://s3.example.com", "", "", "", false, "https://ai.example.com", "model-x"));
        assertThrows(IllegalStateException.class, () -> new ProductionSafetyValidator(
                false, false, "", "", "", "s3", "dorm-files", "https://s3.example.com", "", "", "", false, "https://ai.example.com", "model-x"));
        assertThrows(IllegalStateException.class, () -> new ProductionSafetyValidator(
                false, false, "jdbc:mysql://db/app?useSSL=true&verifyServerCertificate=true", "app", "strong-password", "local", "", "", "", "", "", false,
                "https://ai.example.com", "model-x"));
        assertThrows(IllegalStateException.class, () -> new ProductionSafetyValidator(
                false, false, "jdbc:mysql://db/app?useSSL=true&verifyServerCertificate=true", "app", "strong-password", "s3", "dorm-files", "https://s3.example.com", "", "", "", true,
                "https://ai.example.com", "model-x"));
        assertThrows(IllegalStateException.class, () -> new ProductionSafetyValidator(
                false, false, "jdbc:mysql://db/app?useSSL=true&verifyServerCertificate=true", "app", "strong-password", "s3", "dorm-files", "https://s3.example.com", "", "", "", false,
                "http://127.0.0.1:8001", "model-x"));
    }

    @Test
    @DisplayName("生产数据库必须启用 TLS 并校验服务端证书")
    void rejectsUnverifiedDatabaseTls() {
        assertThrows(IllegalStateException.class, () -> new ProductionSafetyValidator(
                false, false, "jdbc:mysql://db/app?useSSL=true&verifyServerCertificate=false",
                "app", "strong-password", "s3", "dorm-files", "https://s3.example.com",
                "", "", "", false, "https://ai.example.com", "model-x"));
    }

    @Test
    @DisplayName("生产 S3 endpoint 必须使用 HTTPS")
    void rejectsInsecureS3Endpoint() {
        assertThrows(IllegalStateException.class, () ->
                validator("", "", "", "https://ai.example.com", "http://s3.example.com"));
    }

    @Test
    @DisplayName("S3 静态凭据必须成对出现，Session Token 不能单独使用")
    void rejectsIncompleteS3Credentials() {
        assertThrows(IllegalStateException.class, () -> validator("access-only", "", "", "https://ai.example.com"));
        assertThrows(IllegalStateException.class, () -> validator("", "secret-only", "", "https://ai.example.com"));
        assertThrows(IllegalStateException.class, () -> validator("", "", "session-only", "https://ai.example.com"));
    }

    @Test
    @DisplayName("S3 支持 IAM Role 或完整静态凭据，AI 地址必须非本机回环")
    void acceptsIamRoleOrCompleteStaticCredentials() {
        assertThrows(IllegalStateException.class, () -> validator("", "", "", "http://127.0.0.1:8001"));
        assertDoesNotThrow(() -> validator("", "", "", "https://ai.example.com"));
        assertDoesNotThrow(() -> validator("access", "secret", "", "https://ai.example.com"));
        assertDoesNotThrow(() -> validator("access", "secret", "session", "https://ai.example.com"));
    }
}
