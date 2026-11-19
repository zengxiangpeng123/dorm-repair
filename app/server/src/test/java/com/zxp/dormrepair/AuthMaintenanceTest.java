package com.zxp.dormrepair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.zxp.dormrepair.auth.AuthMaintenanceService;
import com.zxp.dormrepair.auth.InMemoryAuthSessionRepository;
import com.zxp.dormrepair.auth.TokenStore;
import com.zxp.dormrepair.domain.Actor;
import com.zxp.dormrepair.statemachine.ActorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthMaintenanceTest {

    @Test
    @DisplayName("过期和已撤销会话会被维护任务清理，活跃会话保留")
    void cleanupRemovesExpiredAndRevokedSessions() throws Exception {
        var repo = new InMemoryAuthSessionRepository();
        var store = new TokenStore(repo, new com.zxp.dormrepair.auth.InMemoryUserRepository());
        var maintenance = new AuthMaintenanceService(store);
        Actor student = new Actor(ActorType.STUDENT, "S001", "张同学");

        store.issue(student, "S001", 60_000);
        TokenStore.Session revoked = store.issue(student, "S001", 60_000);
        store.revoke(revoked.token());

        Thread.sleep(5);
        maintenance.cleanup();

        assertEquals(1, repo.count());
    }

    @Test
    @DisplayName("同一账号只保留最近指定数量的活动会话")
    void keepsOnlyNewestActiveSessions() {
        var repo = new InMemoryAuthSessionRepository();
        var store = new TokenStore(repo, new com.zxp.dormrepair.auth.InMemoryUserRepository(), 2);
        Actor student = new Actor(ActorType.STUDENT, "S001", "张同学");
        TokenStore.Session first = store.issue(student, "S001", 60_000);
        TokenStore.Session second = store.issue(student, "S001", 60_000);
        TokenStore.Session third = store.issue(student, "S001", 60_000);

        assertNull(store.resolve(first.token()));
        assertNotNull(store.resolve(second.token()));
        assertNotNull(store.resolve(third.token()));
    }

    @Test
    @DisplayName("账号停用后已有令牌立即失效")
    void disabledAccountInvalidatesExistingToken() {
        var users = new com.zxp.dormrepair.auth.InMemoryUserRepository();
        var store = new TokenStore(new InMemoryAuthSessionRepository(), users);
        Actor student = new Actor(ActorType.STUDENT, "S001", "张同学");
        TokenStore.Session issued = store.issue(student, "S001", 60_000);
        assertNotNull(store.resolve(issued.token()));

        users.setActive("S001", false);

        assertNull(store.resolve(issued.token()));
    }
}
