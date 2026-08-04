package com.fzdzzj.lifehabitassistant.config;

import com.fzdzzj.lifehabitassistant.server.service.AiConversationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiConversationProperties.class)
public class AiConversationConfig {
}
