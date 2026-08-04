package com.fzdzzj.lifehabitassistant;

import com.fzdzzj.lifehabitassistant.pojo.DailyGoal;
import com.fzdzzj.lifehabitassistant.pojo.DailyGoals;
import com.fzdzzj.lifehabitassistant.pojo.GoalDtos;
import com.fzdzzj.lifehabitassistant.pojo.User;
import com.fzdzzj.lifehabitassistant.config.ReportCache;
import com.fzdzzj.lifehabitassistant.config.ReportProperties;
import com.fzdzzj.lifehabitassistant.config.AiAdviceCache;
import com.fzdzzj.lifehabitassistant.config.UserCacheEvictor;
import com.fzdzzj.lifehabitassistant.server.dao.DailyGoalRepository;
import com.fzdzzj.lifehabitassistant.server.service.CurrentUser;
import com.fzdzzj.lifehabitassistant.server.service.GoalService;
import com.fzdzzj.lifehabitassistant.server.service.HealthThresholds;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GoalServiceTest {
    private static final DailyGoals DEFAULT_GOALS = new DailyGoals(420, 540, 1500, 30, 3);

    @Test
    void getShouldFallBackToGlobalDefaultsWhenNoCustomGoal() {
        DailyGoalRepository repository = mock(DailyGoalRepository.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        User user = new User("demo", "hash");
        when(currentUser.require()).thenReturn(user);
        when(repository.findByUser(user)).thenReturn(Optional.empty());

        assertEquals(DEFAULT_GOALS, service(repository, currentUser).get());
        verify(repository).findByUser(user);
    }

    @Test
    void saveShouldCreateRowFirstAndUpdateTheSameRowNextTime() {
        DailyGoalRepository repository = mock(DailyGoalRepository.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        User user = new User("demo", "hash");
        DailyGoals first = new DailyGoals(480, 600, 2000, 45, 4);
        DailyGoals second = new DailyGoals(360, 480, 1200, 20, 2);
        DailyGoal existing = new DailyGoal(user, first);
        when(currentUser.require()).thenReturn(user);
        when(repository.findByUser(user)).thenReturn(Optional.empty()).thenReturn(Optional.of(existing));
        when(repository.save(any(DailyGoal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GoalService goalService = service(repository, currentUser);

        assertEquals(first, goalService.save(request(first)));
        assertEquals(second, goalService.save(request(second)));
        ArgumentCaptor<DailyGoal> captor = ArgumentCaptor.forClass(DailyGoal.class);
        verify(repository, times(2)).save(captor.capture());
        assertEquals(first, captor.getAllValues().get(0).toGoals());
        assertEquals(second, captor.getAllValues().get(1).toGoals());
        assertSame(existing, captor.getAllValues().get(1));
    }

    @Test
    void saveShouldRejectMinimumSleepAboveMaximum() {
        DailyGoalRepository repository = mock(DailyGoalRepository.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        GoalService goalService = service(repository, currentUser);

        assertThrows(IllegalArgumentException.class,
                () -> goalService.save(request(new DailyGoals(600, 480, 1500, 30, 3))));
        verify(repository, never()).save(any());
    }

    @Test
    void resetShouldDeleteRowAndReturnDefaults() {
        DailyGoalRepository repository = mock(DailyGoalRepository.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        User user = new User("demo", "hash");
        when(currentUser.require()).thenReturn(user);

        assertEquals(DEFAULT_GOALS, service(repository, currentUser).reset());
        verify(repository).deleteByUser(user);
    }

    @Test
    void effectiveShouldResolveForLoadedUserWithoutTouchingRequestContext() {
        DailyGoalRepository repository = mock(DailyGoalRepository.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        User user = new User("demo", "hash");
        DailyGoals custom = new DailyGoals(360, 480, 1200, 20, 2);
        when(repository.findByUser(user)).thenReturn(Optional.of(new DailyGoal(user, custom)));

        assertEquals(custom, service(repository, currentUser).effective(user));
        verifyNoInteractions(currentUser);
    }

    private GoalService service(DailyGoalRepository repository, CurrentUser currentUser) {
        return new GoalService(repository, currentUser, new HealthThresholds(420, 540, 1500, 30, 3),
                new UserCacheEvictor(new ReportCache(new ReportProperties(Duration.ofMinutes(10), 128)),
                        mock(AiAdviceCache.class)));
    }

    private GoalDtos.GoalRequest request(DailyGoals goals) {
        return new GoalDtos.GoalRequest(goals.minimumSleepMinutes(), goals.maximumSleepMinutes(),
                goals.minimumHydrationMl(), goals.minimumExerciseMinutes(), goals.minimumDietScore());
    }
}
