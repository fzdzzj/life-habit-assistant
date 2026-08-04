package com.fzdzzj.lifehabitassistant.server.service;

import com.fzdzzj.lifehabitassistant.pojo.DailyGoal;
import com.fzdzzj.lifehabitassistant.pojo.DailyGoals;
import com.fzdzzj.lifehabitassistant.pojo.GoalDtos;
import com.fzdzzj.lifehabitassistant.pojo.User;
import com.fzdzzj.lifehabitassistant.server.dao.DailyGoalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoalService {
    private final DailyGoalRepository goals;
    private final CurrentUser currentUser;
    private final HealthThresholds thresholds;

    public GoalService(DailyGoalRepository goals, CurrentUser currentUser, HealthThresholds thresholds) {
        this.goals = goals;
        this.currentUser = currentUser;
        this.thresholds = thresholds;
    }

    @Transactional(readOnly = true)
    public DailyGoals get() {
        return effective(currentUser.require());
    }

    /**
     * Resolves the effective goals for an already-loaded user without touching the request context.
     * Used by HabitService so the user is only loaded once per request.
     */
    public DailyGoals effective(User user) {
        return goals.findByUser(user).map(DailyGoal::toGoals).orElseGet(thresholds::toGoals);
    }

    @Transactional
    public DailyGoals save(GoalDtos.GoalRequest request) {
        if (request.minimumSleepMinutes() > request.maximumSleepMinutes()) {
            throw new IllegalArgumentException("minimumSleepMinutes 不得大于 maximumSleepMinutes");
        }
        DailyGoals goals = new DailyGoals(request.minimumSleepMinutes(), request.maximumSleepMinutes(),
                request.minimumHydrationMl(), request.minimumExerciseMinutes(), request.minimumDietScore());
        User user = currentUser.require();
        DailyGoal goal = this.goals.findByUser(user).orElse(null);
        if (goal == null) {
            goal = new DailyGoal(user, goals);
        } else {
            goal.update(goals);
        }
        return this.goals.save(goal).toGoals();
    }

    @Transactional
    public DailyGoals reset() {
        goals.deleteByUser(currentUser.require());
        return thresholds.toGoals();
    }
}
