package com.fzdzzj.lifehabitassistant.server.service;

import com.fzdzzj.lifehabitassistant.pojo.DrinkRecord;
import com.fzdzzj.lifehabitassistant.pojo.DrinkType;
import com.fzdzzj.lifehabitassistant.pojo.HabitRecord;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "app.drink")
public record DrinkHealthRules(Map<DrinkType, BigDecimal> hydrationRatios,
                               int sugaryRiskVolumeMl,
                               int carbonatedSweetRiskVolumeMl,
                               int energyDrinkRiskVolumeMl,
                               int alcoholRiskVolumeMl) {
    private static final EnumSet<DrinkType> RISK_TYPES = EnumSet.of(
            DrinkType.SUGAR_SWEETENED_DRINK, DrinkType.CARBONATED_SWEET_DRINK,
            DrinkType.ENERGY_DRINK, DrinkType.ALCOHOL);

    public DrinkHealthRules {
        if (hydrationRatios == null || !hydrationRatios.keySet().containsAll(EnumSet.allOf(DrinkType.class))) {
            throw new IllegalArgumentException("app.drink.hydration-ratios 必须配置全部饮品类型");
        }
    }

    public int hydrationMl(DrinkRecord record) {
        return hydrationRatios.get(record.getDrinkType())
                .multiply(BigDecimal.valueOf(record.getVolumeMl()))
                .setScale(0, RoundingMode.HALF_UP)
                .intValueExact();
    }

    public int hydrationMl(HabitRecord record) {
        return record.getDrinkRecords().stream().mapToInt(this::hydrationMl).sum();
    }

    public int riskDrinkVolumeMl(HabitRecord record) {
        return record.getDrinkRecords().stream().filter(this::isRiskDrink).mapToInt(DrinkRecord::getVolumeMl).sum();
    }

    public boolean isRiskDrink(DrinkRecord record) {
        return RISK_TYPES.contains(record.getDrinkType());
    }

    public List<String> riskMessages(List<HabitRecord> records) {
        java.util.EnumMap<DrinkType, Integer> volumes = new java.util.EnumMap<>(DrinkType.class);
        records.stream().flatMap(record -> record.getDrinkRecords().stream())
                .forEach(record -> volumes.merge(record.getDrinkType(), record.getVolumeMl(), Integer::sum));
        return riskMessages(volumes);
    }

    public List<String> riskMessages(Map<DrinkType, Integer> volumes) {
        int sugary = volumes.getOrDefault(DrinkType.SUGAR_SWEETENED_DRINK, 0);
        int carbonated = volumes.getOrDefault(DrinkType.CARBONATED_SWEET_DRINK, 0);
        int energy = volumes.getOrDefault(DrinkType.ENERGY_DRINK, 0);
        int alcohol = volumes.getOrDefault(DrinkType.ALCOHOL, 0);
        java.util.ArrayList<String> messages = new java.util.ArrayList<>();
        if (sugary >= sugaryRiskVolumeMl) messages.add("含糖饮料累计达到 " + sugary + " ml");
        if (carbonated >= carbonatedSweetRiskVolumeMl) messages.add("含糖碳酸饮料累计达到 " + carbonated + " ml");
        if (energy >= energyDrinkRiskVolumeMl) messages.add("本周期饮用了能量饮料（" + energy + " ml）");
        if (alcohol >= alcoholRiskVolumeMl) messages.add("本周期饮用了酒精饮品（" + alcohol + " ml）");
        return List.copyOf(messages);
    }

}
