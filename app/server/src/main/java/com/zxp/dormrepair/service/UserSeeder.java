package com.zxp.dormrepair.service;

import com.zxp.dormrepair.auth.PasswordHasher;
import com.zxp.dormrepair.auth.UserAccount;
import com.zxp.dormrepair.auth.UserRepository;
import com.zxp.dormrepair.statemachine.ActorType;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时把人员表补齐：库是空的就建六个演示账号，已有的账号如果 password_hash 是空的就补一份。
 *
 * <p>为什么不在 data.sql 里写死哈希：哈希的盐是随机的，写死在 SQL 里就成了"所有环境同一个盐"，
 * 而且换口令要手改 SQL。这里放进 Java 里，口令只在一个地方（{@link PasswordHasher#DEFAULT_PASSWORD}）。
 */
@Component
public class UserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UserSeeder.class);

    private final UserRepository users;

    public UserSeeder(UserRepository users) {
        this.users = users;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureUsers();
    }

    /** 幂等：跑几次结果都一样。返回这次真正插进去的人数。 */
    public synchronized int ensureUsers() {
        List<UserAccount> demo = demoUsers();
        int inserted = 0;
        if (users.count() == 0) {
            for (UserAccount u : demo) {
                users.insert(u);
                inserted++;
            }
            log.info("人员表是空的，已建 {} 个演示账号，初始口令 {}",
                    inserted, PasswordHasher.DEFAULT_PASSWORD);
            return inserted;
        }
        int patched = 0;
        for (UserAccount u : demo) {
            UserAccount cur = users.find(u.id()).orElse(null);
            if (cur == null) {
                users.insert(u);
                inserted++;
            } else if (cur.password() == null || cur.password().isBlank()) {
                users.setPassword(cur.id(), PasswordHasher.demoHash());
                patched++;
            }
        }
        if (inserted > 0 || patched > 0) {
            log.info("人员表补齐：新增 {} 个账号，补口令 {} 个", inserted, patched);
        }
        return inserted;
    }

    /** 六个演示账号。学号/工号就是账号，改这里要同步改 CG10 用户手册。 */
    public static List<UserAccount> demoUsers() {
        String hash = PasswordHasher.demoHash();
        return List.of(
                new UserAccount("S001", ActorType.STUDENT, "张同学", null, "B7", "402", "13800000001", true, hash),
                new UserAccount("S002", ActorType.STUDENT, "李同学", null, "B7", "508", "13800000002", true, hash),
                new UserAccount("S003", ActorType.STUDENT, "王同学", null, "B8", "205", "13800000003", true, hash),
                new UserAccount("W0428", ActorType.WORKER, "李建国", "水电班", "B7", null, "13900000428", true, hash),
                new UserAccount("W0512", ActorType.WORKER, "王海涛", "综合班", "B8", null, "13900000512", true, hash),
                new UserAccount("A001", ActorType.ADMIN, "王主任", null, null, null, "13700000001", true, hash));
    }
}
