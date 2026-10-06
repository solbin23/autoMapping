package com.example.aimapper.ai.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.retry.support.RetryTemplate;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;

/**
 * OPENAI_API_KEY와 애플리케이션 설정으로 제한 시간 및 재시도 정책이 적용된 ChatModel을 구성한다.
 */
@Configuration
public class AiMappingConfiguration {
    /** 키가 없거나 AI가 비활성화되면 빈 모델을 제공해 규칙 기반 흐름이 계속 동작하게 한다. */
    @Bean
    MappingAiClient mappingChatModel(@Value("${spring.ai.openai.api-key:}") String key,
            @Value("${mapping.ai.enabled:true}") boolean enabled,
            @Value("${mapping.ai.model:gpt-4o-mini}") String model,
            @Value("${mapping.ai.timeout:20s}") Duration timeout) {
        if (!enabled || key.isBlank()) return new MappingAiClient(Optional.empty());
        var http = HttpClient.newBuilder().connectTimeout(timeout).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(timeout);
        var api = OpenAiApi.builder().apiKey(key)
                .restClientBuilder(RestClient.builder().requestFactory(factory)).build();
        return new MappingAiClient(Optional.of(OpenAiChatModel.builder().openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model(model).maxTokens(1200).temperature(0.0).build())
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).build()).build()));
    }
}
