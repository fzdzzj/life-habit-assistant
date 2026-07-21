package com.fzdzzj.lifehabitassistant.server.service;

import com.fzdzzj.lifehabitassistant.pojo.AnalysisDtos;
import com.fzdzzj.lifehabitassistant.pojo.HabitRecord;

import java.util.List;

public interface AdviceGenerator {
    AnalysisDtos.AnalysisResponse generate(int days, List<HabitRecord> records);
}
