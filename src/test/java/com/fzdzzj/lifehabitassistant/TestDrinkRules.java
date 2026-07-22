package com.fzdzzj.lifehabitassistant;

import com.fzdzzj.lifehabitassistant.pojo.DrinkType;
import com.fzdzzj.lifehabitassistant.server.service.DrinkHealthRules;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

final class TestDrinkRules {
    private TestDrinkRules() {
    }

    static DrinkHealthRules defaults() {
        Map<DrinkType, BigDecimal> ratios = new EnumMap<>(DrinkType.class);
        for (DrinkType type : DrinkType.values()) ratios.put(type, BigDecimal.ZERO);
        ratios.put(DrinkType.WATER, BigDecimal.ONE);
        ratios.put(DrinkType.UNSWEETENED_TEA, new BigDecimal("0.9"));
        ratios.put(DrinkType.COFFEE, new BigDecimal("0.8"));
        ratios.put(DrinkType.MILK, new BigDecimal("0.8"));
        ratios.put(DrinkType.JUICE, new BigDecimal("0.5"));
        return new DrinkHealthRules(ratios, 500, 330, 1, 1);
    }
}
