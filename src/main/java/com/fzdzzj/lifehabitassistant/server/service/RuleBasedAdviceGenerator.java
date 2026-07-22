package com.fzdzzj.lifehabitassistant.server.service;

import com.fzdzzj.lifehabitassistant.pojo.AnalysisDtos;
import com.fzdzzj.lifehabitassistant.pojo.HealthStatistics;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RuleBasedAdviceGenerator implements AdviceGenerator {
    private final HealthThresholds thresholds;
    private final DrinkHealthRules drinkRules;

    public RuleBasedAdviceGenerator(HealthThresholds thresholds, DrinkHealthRules drinkRules) {
        this.thresholds = thresholds;
        this.drinkRules = drinkRules;
    }

    @Override
    public AnalysisDtos.AnalysisResponse generate(int days, HealthStatistics statistics) {
        if (statistics.recordCount() == 0) {
            return new AnalysisDtos.AnalysisResponse(days, 0, "当前周期没有记录，暂时无法形成趋势。",
                    List.of("缺少连续数据"), List.of("请连续记录至少 7 天，再查看个性化建议。"));
        }
        double sleep = statistics.averageSleepHours();
        double diet = statistics.averageDietScore();
        double hydration = statistics.averageHydrationMl();
        double exercise = statistics.totalExerciseMinutes() / (double) statistics.recordCount();
        int moderateEquivalentMinutes = statistics.totalModerateEquivalentExerciseMinutes();
        int strengthTrainingCount = statistics.strengthTrainingCount();
        List<String> risks = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

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
        if (hydration < thresholds.minimumHydrationMl()) {
            risks.add("平均有效补水量不足");
            suggestions.add("优先补充白水或无糖饮品，目标每天至少 " + thresholds.minimumHydrationMl() + " ml 有效补水。");
        }
        for (String drinkRisk : drinkRules.riskMessages(statistics.drinkVolumesByType())) {
            risks.add(drinkRisk);
            suggestions.add("减少对应风险饮品，以白水、无糖茶等替代，避免把饮料等同于有效补水。");
        }
        if (exercise < thresholds.minimumExerciseMinutes()) {
            risks.add("日均运动不足");
            suggestions.add("从每天 " + thresholds.minimumExerciseMinutes() + " 分钟步行或轻运动开始。");
        }
        if (days >= 7 && moderateEquivalentMinutes < 150 * days / 7d) {
            risks.add("中等强度运动当量未达到每周 150 分钟");
            suggestions.add("高强度运动按两倍计入；可安排每周至少 150 分钟中等强度当量运动。");
        }
        if (days >= 7 && strengthTrainingCount < 2d * days / 7d) {
            risks.add("力量训练频次未达到每周 2 次");
            suggestions.add("在安全和自身情况允许的前提下，每周安排至少 2 次力量训练。");
        }
        if (risks.isEmpty()) {
            suggestions.add("本周期指标稳定，请保持当前作息并持续记录。");
        }
        return new AnalysisDtos.AnalysisResponse(days, statistics.recordCount(),
                String.format("已分析 %d 条记录：平均睡眠 %.1f 小时、饮食 %.1f 分、日均运动 %.0f 分钟。",
                        statistics.recordCount(), sleep, diet, exercise), risks, suggestions);
    }
}
