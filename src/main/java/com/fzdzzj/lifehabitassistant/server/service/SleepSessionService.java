package com.fzdzzj.lifehabitassistant.server.service;

import com.fzdzzj.lifehabitassistant.common.ApiException;
import com.fzdzzj.lifehabitassistant.pojo.HabitRecord;
import com.fzdzzj.lifehabitassistant.pojo.SleepSession;
import com.fzdzzj.lifehabitassistant.pojo.SleepSessionDtos;
import com.fzdzzj.lifehabitassistant.pojo.SleepType;
import com.fzdzzj.lifehabitassistant.server.dao.HabitRecordRepository;
import com.fzdzzj.lifehabitassistant.server.dao.SleepSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SleepSessionService {
    private final HabitRecordRepository habits;
    private final SleepSessionRepository sessions;
    private final CurrentUser currentUser;

    public SleepSessionService(HabitRecordRepository habits, SleepSessionRepository sessions, CurrentUser currentUser) {
        this.habits = habits;
        this.sessions = sessions;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<SleepSessionDtos.SleepSessionResponse> list(LocalDate recordDate) {
        return sessions.findByHabitRecordOrderBySleepStartAtAsc(requireHabit(recordDate)).stream().map(this::toResponse).toList();
    }

    @Transactional
    public SleepSessionDtos.SleepSessionResponse create(LocalDate recordDate, SleepSessionDtos.SleepSessionRequest request) {
        HabitRecord habit = requireHabit(recordDate);
        List<SleepSession> existing = sessions.findByHabitRecordOrderBySleepStartAtAsc(habit);
        validate(recordDate, request, existing, null);
        return toResponse(sessions.save(new SleepSession(habit, request.sleepType(), request.sleepStartAt(), request.wakeAt())));
    }

    @Transactional
    public SleepSessionDtos.SleepSessionResponse update(LocalDate recordDate, Long id, SleepSessionDtos.SleepSessionRequest request) {
        HabitRecord habit = requireHabit(recordDate);
        SleepSession session = sessions.findByIdAndHabitRecord(id, habit).orElseThrow(() -> ApiException.notFound("睡眠片段不存在"));
        validate(recordDate, request, sessions.findByHabitRecordOrderBySleepStartAtAsc(habit), id);
        session.update(request.sleepType(), request.sleepStartAt(), request.wakeAt());
        return toResponse(session);
    }

    @Transactional
    public void delete(LocalDate recordDate, Long id) {
        HabitRecord habit = requireHabit(recordDate);
        sessions.delete(sessions.findByIdAndHabitRecord(id, habit).orElseThrow(() -> ApiException.notFound("睡眠片段不存在")));
    }

    private HabitRecord requireHabit(LocalDate recordDate) {
        return habits.findByUserAndRecordDate(currentUser.require(), recordDate).orElseThrow(() -> ApiException.notFound("请先创建当天习惯记录"));
    }

    private void validate(LocalDate recordDate, SleepSessionDtos.SleepSessionRequest request, List<SleepSession> existing, Long currentId) {
        if (Duration.between(request.sleepStartAt(), request.wakeAt()).compareTo(Duration.ofHours(24)) > 0) {
            throw new IllegalArgumentException("单段睡眠时长不得超过 24 小时");
        }
        if (!request.wakeAt().isAfter(request.sleepStartAt())) throw new IllegalArgumentException("wakeAt 必须晚于 sleepStartAt");
        if (!request.wakeAt().toLocalDate().equals(recordDate)) throw new IllegalArgumentException("recordDate 必须等于 wakeAt 的日期");
        if (request.wakeAt().isAfter(LocalDateTime.now())) throw new IllegalArgumentException("wakeAt 不得晚于当前时间");
        if (currentId == null && existing.size() >= 5) throw new IllegalArgumentException("每天最多 5 段睡眠");
        for (SleepSession session : existing) {
            if (currentId != null && currentId.equals(session.getId())) continue;
            if (request.sleepType() == SleepType.NIGHT && session.getSleepType() == SleepType.NIGHT) throw new IllegalArgumentException("每天最多一段夜睡");
            if (request.sleepStartAt().isBefore(session.getWakeAt()) && session.getSleepStartAt().isBefore(request.wakeAt())) throw new IllegalArgumentException("睡眠片段不得重叠");
        }
    }

    private SleepSessionDtos.SleepSessionResponse toResponse(SleepSession session) {
        return new SleepSessionDtos.SleepSessionResponse(session.getId(), session.getSleepType(), session.getSleepStartAt(), session.getWakeAt());
    }
}
