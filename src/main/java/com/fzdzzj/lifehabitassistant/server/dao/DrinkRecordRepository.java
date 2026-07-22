package com.fzdzzj.lifehabitassistant.server.dao;

import com.fzdzzj.lifehabitassistant.pojo.DrinkRecord;
import com.fzdzzj.lifehabitassistant.pojo.HabitRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DrinkRecordRepository extends JpaRepository<DrinkRecord, Long> {
    List<DrinkRecord> findByHabitRecordOrderByRecordedAtAsc(HabitRecord habitRecord);

    Optional<DrinkRecord> findByIdAndHabitRecord(Long id, HabitRecord habitRecord);
}
