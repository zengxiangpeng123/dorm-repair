package com.zxp.dormrepair.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 生产配置的最低安全门禁。缺少数据库凭据、开启演示模式、允许请求头冒充身份或
 * 对象存储凭据组合不完整时，启动即失败。
 */
@Component
@Profile("prod")
public class ProductionSafetyValidator {

    public ProductionSafetyValidator(
            @Value("${app.demo:false}") boolean demo,
            @Value("${app.auth.allow-header:false}") boolean allowHeader,
            @Value("${spring.datasource.url:}") String datasourceUrl,
            @Value("${spring.datasource.username:}") String username,
            @Value("${spring.datasource.password:}") String password,
            @Value("${app.file.storage:local}") String fileStorage,
            @Value("${app.file.s3.bucket:}") String fileBucket,
            @Value("${app.file.s3.access-key:}") String s3AccessKey,
            @Value("${app.file.s3.secret-key:}") String s3SecretKey,
            @Value("${app.file.s3.session-token:}") String s3SessionToken,
            @Value("${app.file.allow-legacy-demo-urls:false}") boolean legacyDemoPhotos,
            @Value("${app.ai.base-url:}") String aiBaseUrl,
            @Value("${app.ai.model:}") String aiModel) {
        if (demo) {
            throw new IllegalStateException("生产环境禁止开启 app.demo");
        }
        if (allowHeader) {
            throw new IllegalStateException("生产环境禁止开启 app.auth.allow-header");
        }
        if (datasourceUrl.isBlank() || username.isBlank() || password.isBlank()) {
            throw new IllegalStateException("生产环境必须显式配置数据库 URL、账号和口令");
        }
        if (password.equals("123456") || password.equals("root")) {
            throw new IllegalStateException("生产环境禁止使用默认数据库口令");
        }
        if (!"s3".equalsIgnoreCase(fileStorage) || fileBucket.isBlank()) {
            throw new IllegalStateException("生产环境必须使用私有 S3 对象存储并配置 bucket");
        }
        boolean hasAccess = s3AccessKey != null && !s3AccessKey.isBlank();
        boolean hasSecret = s3SecretKey != null && !s3SecretKey.isBlank();
        boolean hasSession = s3SessionToken != null && !s3SessionToken.isBlank();
        if (hasAccess != hasSecret) {
            throw new IllegalStateException("生产环境 S3 Access Key 与 Secret Key 必须同时配置");
        }
        if (hasSession && !hasAccess) {
            throw new IllegalStateException("生产环境 S3 Session Token 只能配合静态凭据使用");
        }
        if (legacyDemoPhotos) {
            throw new IllegalStateException("生产环境禁止接受 demo:// 照片");
        }
        if (aiBaseUrl.isBlank() || aiModel.isBlank()) {
            throw new IllegalStateException("生产环境必须显式配置 AI 服务地址和模型");
        }
        if (aiBaseUrl.contains("127.0.0.1") || aiBaseUrl.contains("localhost")) {
            throw new IllegalStateException("生产环境禁止使用本机 AI 服务地址");
        }
    }
}
