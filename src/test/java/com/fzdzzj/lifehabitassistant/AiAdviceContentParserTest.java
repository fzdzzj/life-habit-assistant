package com.fzdzzj.lifehabitassistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fzdzzj.lifehabitassistant.pojo.AiAdviceDtos;
import com.fzdzzj.lifehabitassistant.server.service.AiAdviceContentParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiAdviceContentParserTest {
    private final AiAdviceContentParser parser = new AiAdviceContentParser(new ObjectMapper());

    @Test
    void shouldParseValidJsonAndCapRecommendations() {
        String raw = """
                {"periodSummary":"整体稳定","riskExplanation":"睡眠略不足",
                "recommendations":["固定就寝时间","晚餐少用屏幕","午后小睡","第四条不应出现"],
                "nextPeriodPlan":"每天记录","encouragement":"继续保持","disclaimer":"仅供健康参考"}
                """;

        AiAdviceDtos.AiAdviceContent content = parser.parse(raw);

        assertEquals("整体稳定", content.periodSummary());
        assertEquals(3, content.recommendations().size());
        assertEquals("固定就寝时间", content.recommendations().get(0));
        assertEquals("每天记录", content.nextPeriodPlan());
    }

    @Test
    void shouldAcceptFencedJson() {
        String raw = "```json\n{\"periodSummary\":\"ok\",\"recommendations\":[]}\n```";

        AiAdviceDtos.AiAdviceContent content = parser.parse(raw);

        assertEquals("ok", content.periodSummary());
        assertEquals("", content.riskExplanation());
    }

    @Test
    void shouldRejectNonJson() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("不是 JSON"));
    }

    @Test
    void shouldRejectJsonArrayRoot() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("[1, 2]"));
    }
}
