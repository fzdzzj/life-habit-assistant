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
                LocalDate.of(2026, 7, 21), 4, "walked");
        when(currentUser.require()).thenReturn(user);
        when(records.findByUserAndRecordDate(user, request.recordDate())).thenReturn(Optional.empty());
        when(records.save(any(HabitRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HabitDtos.HabitResponse response = new HabitService(records, currentUser, TestDrinkRules.defaults()).save(request);

        ArgumentCaptor<HabitRecord> captor = ArgumentCaptor.forClass(HabitRecord.class);
        verify(records).save(captor.capture());
        assertEquals(user, captor.getValue().getUser());
        assertEquals(0, response.sleepMinutes());
        assertEquals("walked", response.note());
    }

    @Test
    void updatesExistingRecordForSameUserAndDate() {
        HabitRecordRepository records = mock(HabitRecordRepository.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        User user = new User("demo", "hash");
        LocalDate date = LocalDate.of(2026, 7, 21);
        HabitRecord existing = new HabitRecord(user, date, 3, null);
        existing.addSleepSession(new com.fzdzzj.lifehabitassistant.pojo.SleepSession(existing, com.fzdzzj.lifehabitassistant.pojo.SleepType.NIGHT, date.minusDays(1).atTime(22, 0), date.atTime(6, 0)));
        HabitDtos.HabitRequest request = new HabitDtos.HabitRequest(date, 5, "updated");
        when(currentUser.require()).thenReturn(user);
        when(records.findByUserAndRecordDate(user, date)).thenReturn(Optional.of(existing));
        when(records.save(existing)).thenReturn(existing);

        HabitDtos.HabitResponse response = new HabitService(records, currentUser, TestDrinkRules.defaults()).save(request);

        verify(records).save(existing);
        assertEquals(480, response.sleepMinutes());
        assertEquals(5, response.dietScore());
        assertEquals("updated", response.note());
    }
}
