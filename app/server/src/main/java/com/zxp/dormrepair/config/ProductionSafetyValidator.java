package com.zxp.dormrepair.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 生产配置的最低安全门禁。只要缺少数据库凭据、开启演示模式或允许请求头冒充身份，
 * 启动即失败，不允许带着演示开关进入生产。
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
            @Value("${app.file.allow-legacy-demo-urls:false}") boolean legacyDemoPhotos) {
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
        if (legacyDemoPhotos) {
            throw new IllegalStateException("生产环境禁止接受 demo:// 照片");
        }
    }
}
