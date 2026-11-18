package com.zxp.dormrepair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxp.dormrepair.auth.InMemoryUserRepository;
import com.zxp.dormrepair.auth.PasswordHasher;
import com.zxp.dormrepair.service.BootstrapAdminInitializer;
import com.zxp.dormrepair.statemachine.ActorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BootstrapAdminInitializerTest {

    @Test
    @DisplayName("空管理员表可用显式口令创建首个管理员，且口令只存哈希")
    void createsFirstAdminFromExplicitCredentials() {
        var users = new InMemoryUserRepository();
        users.clear();
        var initializer = new BootstrapAdminInitializer(users, "A9001", "生产管理员",
                "Strong-Admin-Pass-2026");

        initializer.run(null);

        var admin = users.find("A9001").orElseThrow();
        assertEquals(ActorType.ADMIN, admin.role());
        assertTrue(admin.password().startsWith("pbkdf2$"));
        assertTrue(PasswordHasher.verify("Strong-Admin-Pass-2026", admin.password()));
    }

    @Test
    @DisplayName("已有管理员时不会被初始化口令覆盖")
    void doesNotOverwriteExistingAdmin() {
        var users = new InMemoryUserRepository();
        var existing = users.find("A001").orElseThrow();
        var initializer = new BootstrapAdminInitializer(users, "A9001", "生产管理员",
                "Strong-Admin-Pass-2026");

        initializer.run(null);

        assertEquals(existing, users.find("A001").orElseThrow());
        assertTrue(users.find("A9001").isEmpty());
    }
}
