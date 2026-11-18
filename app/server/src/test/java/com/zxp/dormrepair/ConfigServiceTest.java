package com.zxp.dormrepair;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zxp.dormrepair.service.ConfigService;
import com.zxp.dormrepair.store.mapper.ConfigMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConfigServiceTest {

    @Test
    @DisplayName("内存配置默认值稳定，更新后立即可读")
    void updatesConfiguration() {
        ConfigService config = new ConfigService(new EmptyMapper(), "memory");
        assertEquals(200, config.budgetLine());
        config.update(Map.of(ConfigService.BUDGET_LINE, 350));
        assertEquals(350, config.budgetLine());
    }

    private static final class EmptyMapper implements ConfigMapper {
        @Override
        public List<Map<String, Object>> all() {
            return List.of();
        }

        @Override
        public int upsert(String k, String v, String at) {
            return 0;
        }
    }
}
