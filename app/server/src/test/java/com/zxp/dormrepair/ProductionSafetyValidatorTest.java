package com.zxp.dormrepair;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zxp.dormrepair.config.ProductionSafetyValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductionSafetyValidatorTest {

    @Test
    @DisplayName("生产禁止演示模式、身份头和默认数据库口令")
    void rejectsUnsafeProductionConfiguration() {
        assertThrows(IllegalStateException.class, () -> new ProductionSafetyValidator(
                true, false, "jdbc:mysql://db/app", "app", "strong-password"));
        assertThrows(IllegalStateException.class, () -> new ProductionSafetyValidator(
                false, true, "jdbc:mysql://db/app", "app", "strong-password"));
        assertThrows(IllegalStateException.class, () -> new ProductionSafetyValidator(
                false, false, "jdbc:mysql://db/app", "root", "123456"));
        assertThrows(IllegalStateException.class, () -> new ProductionSafetyValidator(
                false, false, "", "", ""));
    }

    @Test
    @DisplayName("生产安全配置可以启动")
    void acceptsSafeProductionConfiguration() {
        assertDoesNotThrow(() -> new ProductionSafetyValidator(
                false, false, "jdbc:mysql://db/app", "app", "strong-password"));
    }
}
