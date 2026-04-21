package com.preplit.router;

import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ModelRouterFactory {

    @Bean
    public StreamingChatLanguageModel streamingChatModel(
            @Value("${ai.provider}") String provider,
            @Value("${ai.model}") String model,
            @Value("${ai.api-key}") String apiKey,
            @Value("${ai.base-url:#{null}}") String baseUrl) {
        return switch (provider.toLowerCase()) {
            case "openai", "sarvam" -> {
                var builder = OpenAiStreamingChatModel.builder()
                        .apiKey(apiKey)
                        .modelName(model);
                if (baseUrl != null && !baseUrl.isBlank()) builder.baseUrl(baseUrl);
                yield builder.build();
            }
            case "anthropic" -> AnthropicStreamingChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(model)
                    .build();
            default -> throw new IllegalStateException("Unsupported AI provider: " + provider);
        };
    }

    @Bean
    public ChatLanguageModel chatLanguageModel(
            @Value("${ai.provider}") String provider,
            @Value("${ai.model}") String model,
            @Value("${ai.api-key}") String apiKey,
            @Value("${ai.base-url:#{null}}") String baseUrl) {
        return switch (provider.toLowerCase()) {
            case "openai", "sarvam" -> {
                var builder = OpenAiChatModel.builder()
                        .apiKey(apiKey)
                        .modelName(model);
                if (baseUrl != null && !baseUrl.isBlank()) builder.baseUrl(baseUrl);
                yield builder.build();
            }
            case "anthropic" -> AnthropicChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(model)
                    .build();
            default -> throw new IllegalStateException("Unsupported AI provider: " + provider);
        };
    }
}
