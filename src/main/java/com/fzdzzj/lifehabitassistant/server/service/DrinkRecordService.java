package com.fzdzzj.lifehabitassistant.server.service;

import com.fzdzzj.lifehabitassistant.common.ApiException;
import com.fzdzzj.lifehabitassistant.pojo.DrinkRecord;
import com.fzdzzj.lifehabitassistant.pojo.DrinkRecordDtos;
import com.fzdzzj.lifehabitassistant.pojo.DrinkType;
import com.fzdzzj.lifehabitassistant.pojo.HabitRecord;
import com.fzdzzj.lifehabitassistant.server.dao.DrinkRecordRepository;
import com.fzdzzj.lifehabitassistant.server.dao.HabitRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class DrinkRecordService {
    private final HabitRecordRepository habits;
    private final DrinkRecordRepository drinks;
    private final CurrentUser currentUser;
    private final DrinkHealthRules rules;

    public DrinkRecordService(HabitRecordRepository habits, DrinkRecordRepository drinks, CurrentUser currentUser,
                              DrinkHealthRules rules) {
        this.habits = habits;
        this.drinks = drinks;
        this.currentUser = currentUser;
        this.rules = rules;
    }

    @Transactional(readOnly = true)
    public List<DrinkRecordDtos.DrinkRecordResponse> list(LocalDate recordDate) {
        return drinks.findByHabitRecordOrderByRecordedAtAsc(requireHabit(recordDate)).stream().map(this::toResponse).toList();
    }

    @Transactional
    public DrinkRecordDtos.DrinkRecordResponse create(LocalDate recordDate, DrinkRecordDtos.DrinkRecordRequest request) {
        HabitRecord habit = requireHabit(recordDate);
        validate(recordDate, request);
        return toResponse(drinks.save(new DrinkRecord(habit, request.drinkType(), request.otherName(), request.volumeMl(), request.recordedAt(), request.note())));
    }

    @Transactional
    public DrinkRecordDtos.DrinkRecordResponse update(LocalDate recordDate, Long id, DrinkRecordDtos.DrinkRecordRequest request) {
        HabitRecord habit = requireHabit(recordDate);
        DrinkRecord drink = drinks.findByIdAndHabitRecord(id, habit).orElseThrow(() -> ApiException.notFound("饮品记录不存在"));
        validate(recordDate, request);
        drink.update(request.drinkType(), request.otherName(), request.volumeMl(), request.recordedAt(), request.note());
        return toResponse(drink);
    }

    @Transactional
    public void delete(LocalDate recordDate, Long id) {
        HabitRecord habit = requireHabit(recordDate);
        drinks.delete(drinks.findByIdAndHabitRecord(id, habit).orElseThrow(() -> ApiException.notFound("饮品记录不存在")));
    }

    private HabitRecord requireHabit(LocalDate recordDate) {
        return habits.findByUserAndRecordDate(currentUser.require(), recordDate)
                .orElseThrow(() -> ApiException.notFound("请先创建当天习惯记录"));
    }

    private void validate(LocalDate recordDate, DrinkRecordDtos.DrinkRecordRequest request) {
        if (!request.recordedAt().toLocalDate().equals(recordDate)) throw new IllegalArgumentException("recordDate 必须等于 recordedAt 的日期");
        if (request.recordedAt().isAfter(LocalDateTime.now())) throw new IllegalArgumentException("recordedAt 不得晚于当前时间");
        if (request.drinkType() == DrinkType.OTHER && (request.otherName() == null || request.otherName().isBlank())) throw new IllegalArgumentException("drinkType 为 OTHER 时必须填写 otherName");
        if (request.drinkType() != DrinkType.OTHER && request.otherName() != null && !request.otherName().isBlank()) throw new IllegalArgumentException("仅 drinkType 为 OTHER 时可以填写 otherName");
    }

    private DrinkRecordDtos.DrinkRecordResponse toResponse(DrinkRecord record) {
        return new DrinkRecordDtos.DrinkRecordResponse(record.getId(), record.getDrinkType(), record.getOtherName(),
                record.getVolumeMl(), rules.hydrationMl(record), rules.isRiskDrink(record), record.getRecordedAt(), record.getNote());
    }
}
