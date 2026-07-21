package com.fzdzzj.lifehabitassistant;

import com.fzdzzj.lifehabitassistant.pojo.HabitDtos;
import com.fzdzzj.lifehabitassistant.pojo.HabitRecord;
import com.fzdzzj.lifehabitassistant.pojo.User;
import com.fzdzzj.lifehabitassistant.server.dao.HabitRecordRepository;
import com.fzdzzj.lifehabitassistant.server.service.CurrentUser;
import com.fzdzzj.lifehabitassistant.server.service.HabitService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HabitServiceTest {
    @Test
    void savesNewRecordForCurrentUser() {
        HabitRecordRepository records = mock(HabitRecordRepository.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        User user = new User("demo", "hash");
        HabitDtos.HabitRequest request = new HabitDtos.HabitRequest(
                LocalDate.of(2026, 7, 21), LocalTime.of(23, 30), LocalTime.of(7, 0), 4, 45, 1800, "walked");
        when(currentUser.require()).thenReturn(user);
        when(records.findByUserAndRecordDate(user, request.recordDate())).thenReturn(Optional.empty());
        when(records.save(any(HabitRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HabitDtos.HabitResponse response = new HabitService(records, currentUser).save(request);

        ArgumentCaptor<HabitRecord> captor = ArgumentCaptor.forClass(HabitRecord.class);
        verify(records).save(captor.capture());
        assertEquals(user, captor.getValue().getUser());
        assertEquals(450, response.sleepMinutes());
        assertEquals("walked", response.note());
    }

    @Test
    void updatesExistingRecordForSameUserAndDate() {
        HabitRecordRepository records = mock(HabitRecordRepository.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        User user = new User("demo", "hash");
        LocalDate date = LocalDate.of(2026, 7, 21);
        HabitRecord existing = new HabitRecord(user, date, LocalTime.of(22, 0), LocalTime.of(6, 0), 3, 20, 1200, null);
        HabitDtos.HabitRequest request = new HabitDtos.HabitRequest(date, LocalTime.of(23, 0), LocalTime.of(7, 0), 5, 60, 2000, "updated");
        when(currentUser.require()).thenReturn(user);
        when(records.findByUserAndRecordDate(user, date)).thenReturn(Optional.of(existing));
        when(records.save(existing)).thenReturn(existing);

        HabitDtos.HabitResponse response = new HabitService(records, currentUser).save(request);

        verify(records).save(existing);
        assertEquals(480, response.sleepMinutes());
        assertEquals(5, response.dietScore());
        assertEquals("updated", response.note());
    }
}
