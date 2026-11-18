package com.zxp.dormrepair.auth;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * 口令哈希。用 JDK 自带的 PBKDF2WithHmacSHA256，不引第三方库。
 *
 * <p>存进库里的格式是 {@code pbkdf2$120000$<saltBase64>$<hashBase64>}，迭代次数写在串里，
 * 以后调参不用改老数据的验证逻辑。
 *
 * <p>演示账号共用一个初始口令 {@link #DEFAULT_PASSWORD}，所以它们也共用一份哈希
 * （同一口令同一盐）。真实账号每人一份随机盐，{@link #hash(String)} 每次都会重新取盐。
 */
public final class PasswordHasher {

    /** 演示账号的初始口令。改它要同时改 CG10 用户手册与本类注释。 */
    public static final String DEFAULT_PASSWORD = "dorm@2026";

    private static final String ALG = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;

    private PasswordHasher() {
    }

    /** 生成 {@code pbkdf2$iter$salt$hash}。每次调用都用新的随机盐。 */
    public static String hash(String raw) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("口令不能为空");
        }
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        return "pbkdf2$" + ITERATIONS + "$" + b64(salt) + "$" + b64(derive(raw, salt, ITERATIONS));
    }

    /** 校验。库里是 NULL / 格式不认识 / 口令不对，一律返回 false，不抛异常。 */
    public static boolean verify(String raw, String stored) {
        if (raw == null || raw.isEmpty() || stored == null || stored.isBlank()) {
            return false;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !"pbkdf2".equals(parts[0])) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] want = Base64.getDecoder().decode(parts[3]);
            byte[] got = derive(raw, salt, iterations);
            return MessageDigest.isEqual(want, got);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** 给种子数据用：六个演示账号共用一份哈希，省下六次 12 万轮推导。 */
    public static String demoHash() {
        return DEMO_HASH;
    }

    private static final String DEMO_HASH = hash(DEFAULT_PASSWORD);

    private static byte[] derive(String raw, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(raw.toCharArray(), salt, iterations, KEY_BITS);
            return SecretKeyFactory.getInstance(ALG).generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("口令哈希失败：" + e.getMessage(), e);
        }
    }

    private static String b64(byte[] b) {
        return Base64.getEncoder().encodeToString(b);
    }
}
