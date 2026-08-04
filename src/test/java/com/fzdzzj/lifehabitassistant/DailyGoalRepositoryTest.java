package com.fzdzzj.lifehabitassistant;

import com.fzdzzj.lifehabitassistant.pojo.DailyGoal;
import com.fzdzzj.lifehabitassistant.pojo.DailyGoals;
import com.fzdzzj.lifehabitassistant.pojo.User;
import com.fzdzzj.lifehabitassistant.server.dao.DailyGoalRepository;
import com.fzdzzj.lifehabitassistant.server.dao.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
class DailyGoalRepositoryTest {
    @Autowired
    private DailyGoalRepository goals;
    @Autowired
    private UserRepository users;

    @Test
    void duplicateGoalForSameUserShouldViolateUniqueConstraint() {
        User user = users.save(new User("goals-" + UUID.randomUUID(), "hash"));
        goals.saveAndFlush(new DailyGoal(user, new DailyGoals(420, 540, 1500, 30, 3)));

        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> goals.saveAndFlush(new DailyGoal(user, new DailyGoals(360, 480, 1200, 20, 2))));
    }

    @Test
    void goalsShouldBeIsolatedPerUser() {
        User first = users.save(new User("goals-a-" + UUID.randomUUID(), "hash"));
        User second = users.save(new User("goals-b-" + UUID.randomUUID(), "hash"));
        DailyGoals custom = new DailyGoals(480, 600, 2000, 45, 4);
        goals.saveAndFlush(new DailyGoal(first, custom));

        assertEquals(custom, goals.findByUser(first).orElseThrow().toGoals());
        assertTrue(goals.findByUser(second).isEmpty());
    }

    @Test
    void deleteByUserShouldRemoveOnlyThatUsersGoal() {
        User first = users.save(new User("goals-c-" + UUID.randomUUID(), "hash"));
        User second = users.save(new User("goals-d-" + UUID.randomUUID(), "hash"));
        goals.saveAndFlush(new DailyGoal(first, new DailyGoals(480, 600, 2000, 45, 4)));
        goals.saveAndFlush(new DailyGoal(second, new DailyGoals(360, 480, 1200, 20, 2)));

        goals.deleteByUser(first);

        assertTrue(goals.findByUser(first).isEmpty());
        assertTrue(goals.findByUser(second).isPresent());
    }
}
