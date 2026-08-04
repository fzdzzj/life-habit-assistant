package com.fzdzzj.lifehabitassistant.config;

import com.fzdzzj.lifehabitassistant.server.service.AiAdviceProperties;
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
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(properties.timeoutSeconds()));
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.timeoutSeconds()));
        RestClient.Builder restClientBuilder = RestClient.builder().requestFactory(requestFactory);

        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(properties.baseUrl())
                .apiKey(properties.apiKey())
                .restClientBuilder(restClientBuilder)
                .build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(properties.model())
                .temperature(0.4)
                .build();
        OpenAiChatModel model = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .build();
        return ChatClient.builder(model).build();
    }
}
