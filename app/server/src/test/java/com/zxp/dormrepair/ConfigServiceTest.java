package com.zxp.dormrepair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    @DisplayName("配置值必须是正整数，非法值不会写入缓存")
    void rejectsNonPositiveOrNonIntegerValues() {
        ConfigService config = new ConfigService(new EmptyMapper(), "memory");
        assertThrows(com.zxp.dormrepair.common.BizException.class,
                () -> config.update(Map.of(ConfigService.BUDGET_LINE, 0)));
        assertThrows(com.zxp.dormrepair.common.BizException.class,
                () -> config.update(Map.of(ConfigService.VERIFY_HOURS, -1)));
        assertThrows(com.zxp.dormrepair.common.BizException.class,
                () -> config.update(Map.of(ConfigService.ARCHIVE_DAYS, 1.5)));
        assertThrows(com.zxp.dormrepair.common.BizException.class,
                () -> config.update(Map.of(ConfigService.BUDGET_LINE, "abc")));
        assertEquals(200, config.budgetLine());
        assertEquals(168, config.verifyHours());
        assertEquals(7, config.archiveDays());
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
