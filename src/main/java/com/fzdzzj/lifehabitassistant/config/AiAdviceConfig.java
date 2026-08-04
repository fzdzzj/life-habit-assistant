package com.fzdzzj.lifehabitassistant.config;

import com.fzdzzj.lifehabitassistant.server.service.AiAdviceProperties;
import com.fzdzzj.lifehabitassistant.server.service.AiConversationProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(AiAdviceProperties.class)
public class AiAdviceConfig {

    /**
     * Assembles the Spring AI OpenAI model from the project's own
     * app.ai.advice.* properties so AI_ADVICE_* environment variables keep
     * their existing meaning. The bean is only invoked after AiAdviceService
     * verified that the feature is enabled and a model is configured.
     */
    @Bean
    ChatClient aiAdviceChatClient(AiAdviceProperties properties) {
        return buildChatClient(properties.baseUrl(), properties.apiKey(), properties.model(),
                properties.timeoutSeconds());
    }

    /**
     * Streaming conversation client with a longer timeout than the sync
     * advice client so long answers are not cut off mid-stream. It reuses the
     * same provider key/model/base URL as the sync client.
     */
    @Bean
    ChatClient aiConversationStreamChatClient(AiAdviceProperties properties,
                                              AiConversationProperties conversationProperties) {
        return buildChatClient(properties.baseUrl(), properties.apiKey(), properties.model(),
                conversationProperties.streamTimeoutSeconds());
    }

    private ChatClient buildChatClient(String baseUrl, String apiKey, String model, int timeoutSeconds) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        RestClient.Builder restClientBuilder = RestClient.builder().requestFactory(requestFactory);

        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .restClientBuilder(restClientBuilder)
                .build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .temperature(0.4)
                .build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .build();
        return ChatClient.builder(chatModel).build();
    }
}
