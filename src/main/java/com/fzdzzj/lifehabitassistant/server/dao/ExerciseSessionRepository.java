package com.fzdzzj.lifehabitassistant.server.dao;

import com.fzdzzj.lifehabitassistant.pojo.ExerciseSession;
import com.fzdzzj.lifehabitassistant.pojo.HabitRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExerciseSessionRepository extends JpaRepository<ExerciseSession, Long> {
    List<ExerciseSession> findByHabitRecordOrderByStartedAtAsc(HabitRecord habitRecord);

    Optional<ExerciseSession> findByIdAndHabitRecord(Long id, HabitRecord habitRecord);
}
