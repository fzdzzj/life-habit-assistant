package com.fzdzzj.lifehabitassistant;

import com.fzdzzj.lifehabitassistant.server.service.AiConversationPromptLoader;
import com.fzdzzj.lifehabitassistant.server.service.AiConversationProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiConversationPromptLoaderTest {

    @Test
    void defaultVersionShouldLoadTheV1Prompt() {
        AiConversationPromptLoader loader = new AiConversationPromptLoader(properties("v1"));

        String prompt = loader.load();

        assertTrue(prompt.contains("多轮对话助手"));
        assertTrue(prompt.contains("不诊断疾病"));
    }

    @Test
    void customVersionShouldLoadItsOwnPromptFile() {
        AiConversationPromptLoader loader = new AiConversationPromptLoader(properties("test"));

        assertEquals("test-conversation-prompt-content", loader.load().stripTrailing());
    }

    @Test
    void invalidVersionShouldBeRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new AiConversationPromptLoader(properties("../evil")));
    }

    private AiConversationProperties properties(String version) {
        return new AiConversationProperties(false, 7, 10, 2000, version);
    }
}
