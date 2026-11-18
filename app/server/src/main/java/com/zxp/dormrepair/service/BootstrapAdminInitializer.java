package com.zxp.dormrepair.service;

import com.zxp.dormrepair.auth.PasswordHasher;
import com.zxp.dormrepair.auth.UserAccount;
import com.zxp.dormrepair.auth.UserRepository;
import com.zxp.dormrepair.statemachine.ActorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 首次生产初始化：只在管理员表为空时创建一个管理员。
 * 账号、姓名和口令必须由部署方显式注入；不写默认口令，不覆盖已有管理员。
 */
@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final UserRepository users;
    private final String account;
    private final String name;
    private final String password;

    public BootstrapAdminInitializer(UserRepository users,
                                     @Value("${app.bootstrap-admin.account:}") String account,
                                     @Value("${app.bootstrap-admin.name:}") String name,
                                     @Value("${app.bootstrap-admin.password:}") String password) {
        this.users = users;
        this.account = account;
        this.name = name;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.countByRole(ActorType.ADMIN) > 0) {
            return;
        }
        if (account == null || account.isBlank() || name == null || name.isBlank()
                || password == null || password.length() < 12) {
            log.warn("管理员表为空，但未提供合法的 BOOTSTRAP_ADMIN_ACCOUNT/NAME/PASSWORD（口令至少 12 位）");
            return;
        }
        users.insert(new UserAccount(account.trim(), ActorType.ADMIN, name.trim(), null,
                null, null, null, true, PasswordHasher.hash(password)));
        log.info("已创建首个管理员账号：{}（口令不会写入日志）", account.trim());
    }
}
