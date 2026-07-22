package com.fzdzzj.lifehabitassistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.fzdzzj.lifehabitassistant.server.service.DrinkHealthRules;

@SpringBootApplication
@EnableConfigurationProperties(DrinkHealthRules.class)
public class LifeHabitAssistantApplication {
    public static void main(String[] args) {
        SpringApplication.run(LifeHabitAssistantApplication.class, args);
    }
}
