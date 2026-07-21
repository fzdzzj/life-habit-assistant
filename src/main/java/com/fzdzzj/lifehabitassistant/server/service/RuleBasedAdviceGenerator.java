package com.fzdzzj.lifehabitassistant.server.service;

import com.fzdzzj.lifehabitassistant.pojo.AnalysisDtos;
import com.fzdzzj.lifehabitassistant.pojo.HabitRecord;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RuleBasedAdviceGenerator implements AdviceGenerator {
    private final HealthThresholds thresholds;

    public RuleBasedAdviceGenerator(HealthThresholds thresholds) {
        this.thresholds = thresholds;
    }

    public AnalysisDtos.AnalysisResponse generate(int days, List<HabitRecord> records) {
        if (records.isEmpty())
            return new AnalysisDtos.AnalysisResponse(days, 0, "当前周期没有记录，暂时无法形成趋势。", List.of("缺少连续数据"), List.of("请连续记录至少 7 天，再查看个性化建议。"));
        double sleep = records.stream().mapToLong(HabitRecord::sleepMinutes).average().orElse(0) / 60d;
        double diet = records.stream().mapToInt(HabitRecord::getDietScore).average().orElse(0);
        double water = records.stream().mapToInt(HabitRecord::getWaterMl).average().orElse(0);
        double exercise = records.stream().mapToInt(HabitRecord::getExerciseMinutes).average().orElse(0);
        List<String> risks = new ArrayList<>(), suggestions = new ArrayList<>();
        if (sleep < 7) {
            risks.add("平均睡眠不足 7 小时");
            suggestions.add("尝试固定就寝时间，并提前 30 分钟减少屏幕使用。");
        } else if (sleep > 9) {
            risks.add("平均睡眠超过 9 小时");
            suggestions.add("关注白天精神状态，逐步固定起床时间。");
        }
        if (diet < thresholds.minimumDietScore()) {
            risks.add("饮食评分偏低");
            suggestions.add("优先保证规律三餐和蔬菜、蛋白质摄入。");
        }
        if (water < thresholds.minimumWaterMl()) {
            risks.add("平均饮水量不足");
            suggestions.add("将饮水分散到全天，目标每天至少 " + thresholds.minimumWaterMl() + " ml。");
        }
        if (exercise < thresholds.minimumExerciseMinutes()) {
            risks.add("日均运动不足");
            suggestions.add("从每天 " + thresholds.minimumExerciseMinutes() + " 分钟步行或轻运动开始。");
        }
        if (risks.isEmpty()) {
            suggestions.add("本周期指标稳定，请保持当前作息并持续记录。");
        }
        return new AnalysisDtos.AnalysisResponse(days, records.size(), String.format("已分析 %d 条记录：平均睡眠 %.1f 小时、饮食 %.1f 分、日均运动 %.0f 分钟。", records.size(), sleep, diet, exercise), risks, suggestions);
    }
}
