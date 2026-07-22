package com.fzdzzj.lifehabitassistant.server.service;

import com.fzdzzj.lifehabitassistant.common.ApiException;
import com.fzdzzj.lifehabitassistant.pojo.ExerciseSession;
import com.fzdzzj.lifehabitassistant.pojo.ExerciseSessionDtos;
import com.fzdzzj.lifehabitassistant.pojo.ExerciseType;
import com.fzdzzj.lifehabitassistant.pojo.HabitRecord;
import com.fzdzzj.lifehabitassistant.server.dao.ExerciseSessionRepository;
import com.fzdzzj.lifehabitassistant.server.dao.HabitRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ExerciseSessionService {
    private final HabitRecordRepository habits;
    private final ExerciseSessionRepository sessions;
    private final CurrentUser currentUser;

    public ExerciseSessionService(HabitRecordRepository habits, ExerciseSessionRepository sessions, CurrentUser currentUser) {
        this.habits = habits;
        this.sessions = sessions;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<ExerciseSessionDtos.ExerciseSessionResponse> list(LocalDate recordDate) {
        return sessions.findByHabitRecordOrderByStartedAtAsc(requireHabit(recordDate)).stream().map(this::toResponse).toList();
    }

    @Transactional
    public ExerciseSessionDtos.ExerciseSessionResponse create(LocalDate recordDate, ExerciseSessionDtos.ExerciseSessionRequest request) {
        HabitRecord habit = requireHabit(recordDate);
        validate(recordDate, request);
        return toResponse(sessions.save(new ExerciseSession(habit, request.exerciseType(), request.otherName(), request.intensity(), request.durationMinutes(), request.startedAt(), request.distanceKm(), request.caloriesKcal(), request.note())));
    }

    @Transactional
    public ExerciseSessionDtos.ExerciseSessionResponse update(LocalDate recordDate, Long id, ExerciseSessionDtos.ExerciseSessionRequest request) {
        HabitRecord habit = requireHabit(recordDate);
        ExerciseSession session = sessions.findByIdAndHabitRecord(id, habit).orElseThrow(() -> ApiException.notFound("运动记录不存在"));
        validate(recordDate, request);
        session.update(request.exerciseType(), request.otherName(), request.intensity(), request.durationMinutes(), request.startedAt(), request.distanceKm(), request.caloriesKcal(), request.note());
        return toResponse(session);
    }

    @Transactional
    public void delete(LocalDate recordDate, Long id) {
        HabitRecord habit = requireHabit(recordDate);
        sessions.delete(sessions.findByIdAndHabitRecord(id, habit).orElseThrow(() -> ApiException.notFound("运动记录不存在")));
    }

    private HabitRecord requireHabit(LocalDate recordDate) {
        return habits.findByUserAndRecordDate(currentUser.require(), recordDate).orElseThrow(() -> ApiException.notFound("请先创建当天习惯记录"));
    }

    private void validate(LocalDate recordDate, ExerciseSessionDtos.ExerciseSessionRequest request) {
        if (!request.startedAt().toLocalDate().equals(recordDate)) throw new IllegalArgumentException("recordDate 必须等于 startedAt 的日期");
        if (request.startedAt().isAfter(LocalDateTime.now())) throw new IllegalArgumentException("startedAt 不得晚于当前时间");
        if (request.exerciseType() == ExerciseType.OTHER && (request.otherName() == null || request.otherName().isBlank())) throw new IllegalArgumentException("exerciseType 为 OTHER 时必须填写 otherName");
        if (request.exerciseType() != ExerciseType.OTHER && request.otherName() != null && !request.otherName().isBlank()) throw new IllegalArgumentException("仅 exerciseType 为 OTHER 时可以填写 otherName");
    }

    private ExerciseSessionDtos.ExerciseSessionResponse toResponse(ExerciseSession session) {
        return new ExerciseSessionDtos.ExerciseSessionResponse(session.getId(), session.getExerciseType(), session.getOtherName(), session.getIntensity(), session.getDurationMinutes(), session.moderateEquivalentMinutes(), session.getStartedAt(), session.getDistanceKm(), session.getCaloriesKcal(), session.getNote());
    }
}
