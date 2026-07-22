package com.fzdzzj.lifehabitassistant.server.dao;

import com.fzdzzj.lifehabitassistant.pojo.HabitRecord;
import com.fzdzzj.lifehabitassistant.pojo.SleepSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SleepSessionRepository extends JpaRepository<SleepSession, Long> {
    List<SleepSession> findByHabitRecordOrderBySleepStartAtAsc(HabitRecord habitRecord);

    Optional<SleepSession> findByIdAndHabitRecord(Long id, HabitRecord habitRecord);
}
