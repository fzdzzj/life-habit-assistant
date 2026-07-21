package com.fzdzzj.lifehabitassistant.server.dao;

import com.fzdzzj.lifehabitassistant.pojo.HabitRecord;
import com.fzdzzj.lifehabitassistant.pojo.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HabitRecordRepository extends JpaRepository<HabitRecord, Long> {
    Optional<HabitRecord> findByUserAndRecordDate(User user, LocalDate recordDate);

    Page<HabitRecord> findByUserAndRecordDateBetween(User user, LocalDate start, LocalDate end, Pageable pageable);

    List<HabitRecord> findByUserAndRecordDateBetweenOrderByRecordDateAsc(User user, LocalDate start, LocalDate end);
}
