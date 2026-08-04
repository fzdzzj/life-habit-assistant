package com.fzdzzj.lifehabitassistant.server.service;

import com.fzdzzj.lifehabitassistant.pojo.AnalysisDtos;
import com.fzdzzj.lifehabitassistant.pojo.DailyGoals;
import com.fzdzzj.lifehabitassistant.pojo.HealthStatistics;

public interface AdviceGenerator {
    AnalysisDtos.AnalysisResponse generate(int days, HealthStatistics statistics, DailyGoals goals);
}
