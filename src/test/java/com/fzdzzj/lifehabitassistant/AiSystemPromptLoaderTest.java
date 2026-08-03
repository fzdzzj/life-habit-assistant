package com.fzdzzj.lifehabitassistant;

import com.fzdzzj.lifehabitassistant.server.service.AiAdviceProperties;
import com.fzdzzj.lifehabitassistant.server.service.AiSystemPromptLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiSystemPromptLoaderTest {

    @Test
    void defaultVersionShouldLoadTheV1Prompt() {
        AiSystemPromptLoader loader = new AiSystemPromptLoader(properties("v1"));

        String prompt = loader.load();

        assertTrue(prompt.contains("生活习惯助手"));
        assertTrue(prompt.contains("periodSummary"));
        assertTrue(prompt.contains("不构成医疗诊断或治疗建议"));
    }

    @Test
    void customVersionShouldLoadItsOwnPromptFile() {
        AiSystemPromptLoader loader = new AiSystemPromptLoader(properties("test"));

        assertEquals("test-prompt-content", loader.load().stripTrailing());
    }

    @Test
    void invalidVersionShouldBeRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new AiSystemPromptLoader(properties("../evil")));
    }

    @Test
    void missingPromptFileShouldFailFast() {
        assertThrows(IllegalStateException.class,
                () -> new AiSystemPromptLoader(properties("missing")));
    }

    private AiAdviceProperties properties(String version) {
        return new AiAdviceProperties(false, "sk-test", "gpt-demo",
                "https://api.openai.com/v1", 3, 30, 30, version);
    }
}
