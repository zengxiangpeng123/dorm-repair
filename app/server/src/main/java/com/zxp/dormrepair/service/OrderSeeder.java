package com.zxp.dormrepair.service;

import com.zxp.dormrepair.store.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 空库时灌一遍样本单。库里有数据就一个字都不动 —— 重启不会把演示数据冲掉。
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "app.demo", havingValue = "true")
public class OrderSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OrderSeeder.class);

    private final OrderRepository store;
    private final SeedService seeder;

    public OrderSeeder(OrderRepository store, SeedService seeder) {
        this.store = store;
        this.seeder = seeder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (store.size() > 0) {
            log.info("工单表里有 {} 张单，不动它", store.size());
            return;
        }
        int n = seeder.seed();
        log.info("工单表是空的，已灌 {} 张样本单（含 -R1/-R2 返修链与四类例外各一条）", n);
    }
}
