package com.zxp.dormrepair;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        var store = new TokenStore(repo);
        var maintenance = new AuthMaintenanceService(store);
        Actor student = new Actor(ActorType.STUDENT, "S001", "张同学");

        store.issue(student, "S001", 60_000);
        TokenStore.Session revoked = store.issue(student, "S001", 60_000);
        store.revoke(revoked.token());

        Thread.sleep(5);
        maintenance.cleanup();

        assertEquals(1, repo.count());
    }
}
