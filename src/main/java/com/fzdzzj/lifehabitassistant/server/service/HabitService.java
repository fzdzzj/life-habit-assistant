package com.fzdzzj.lifehabitassistant.server.service;

import com.fzdzzj.lifehabitassistant.common.ApiException;
import com.fzdzzj.lifehabitassistant.pojo.HabitDtos;
import com.fzdzzj.lifehabitassistant.pojo.HabitRecord;
import com.fzdzzj.lifehabitassistant.pojo.PageResponse;
import com.fzdzzj.lifehabitassistant.pojo.User;
import com.fzdzzj.lifehabitassistant.server.dao.HabitRecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class HabitService {
    private final HabitRecordRepository records;
    private final CurrentUser currentUser;
    private final DrinkHealthRules drinkRules;

    public HabitService(HabitRecordRepository records, CurrentUser currentUser, DrinkHealthRules drinkRules) {
        this.records = records;
        this.currentUser = currentUser;
        this.drinkRules = drinkRules;
    }

    @Transactional
    public HabitDtos.HabitResponse save(HabitDtos.HabitRequest request) {
        User user = currentUser.require();
        HabitRecord record = records.findByUserAndRecordDate(user, request.recordDate()).orElse(null);
        if (record == null) {
            record = new HabitRecord(user, request.recordDate(), request.dietScore(), request.note());
        } else {
            record.update(request.dietScore(), request.note());
        }
        return toResponse(records.save(record));
    }

    @Transactional(readOnly = true)
    public PageResponse<HabitDtos.HabitResponse> list(LocalDate start, LocalDate end, int page, int size) {
        User user = currentUser.require();
        LocalDate safeEnd = end == null ? LocalDate.now() : end;
        LocalDate safeStart = start == null ? safeEnd.minusDays(29) : start;
        LocalDate today = LocalDate.now();
        if (safeStart.isAfter(today)) throw new IllegalArgumentException("start 不得晚于今天");
        if (safeEnd.isAfter(today)) throw new IllegalArgumentException("end 不得晚于今天");
        if (safeStart.isAfter(safeEnd)) throw new IllegalArgumentException("start 不得晚于 end");
        if (ChronoUnit.DAYS.between(safeStart, safeEnd) > 365) throw new IllegalArgumentException("查询日期范围不得超过 366 天");
        Page<HabitDtos.HabitResponse> result = records.findByUserAndRecordDateBetween(user, safeStart, safeEnd, PageRequest.of(page, size, Sort.by("recordDate").descending())).map(this::toResponse);
        return PageResponse.from(result);
    }

    @Transactional(readOnly = true)
    public HabitDtos.HabitResponse get(LocalDate date) {
        return records.findByUserAndRecordDate(currentUser.require(), date).map(this::toResponse).orElseThrow(() -> ApiException.notFound("当天记录不存在"));
    }

    @Transactional
    public void delete(LocalDate date) {
        HabitRecord record = records.findByUserAndRecordDate(currentUser.require(), date).orElseThrow(() -> ApiException.notFound("当天记录不存在"));
        records.delete(record);
    }

    @Transactional(readOnly = true)
    public List<HabitRecord> range(LocalDate start, LocalDate end) {
        return records.findByUserAndRecordDateBetweenOrderByRecordDateAsc(currentUser.require(), start, end);
    }

    public HabitDtos.HabitResponse toResponse(HabitRecord r) {
        long minutes = r.sleepMinutes();
        int hydrationMl = drinkRules.hydrationMl(r);
        return new HabitDtos.HabitResponse(r.getRecordDate(), minutes, Math.round(minutes / 6.0) / 10.0,
                r.getDietScore(), r.exerciseMinutes(), r.moderateEquivalentExerciseMinutes(), hydrationMl,
                drinkRules.riskDrinkVolumeMl(r), r.getNote(), dailyEvaluation(r, hydrationMl));
    }

    private String dailyEvaluation(HabitRecord r, int hydrationMl) {
        int pass = 0;
        long sleep = r.sleepMinutes();
        if (sleep >= 420 && sleep <= 540) pass++;
        if (r.getDietScore() >= 3) pass++;
        if (r.moderateEquivalentExerciseMinutes() >= 30) pass++;
        if (hydrationMl >= 1500) pass++;
        return pass >= 4 ? "状态良好" : pass >= 2 ? "可继续改善" : "需要重点关注";
    }
}
