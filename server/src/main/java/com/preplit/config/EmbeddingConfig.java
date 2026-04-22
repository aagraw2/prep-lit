package com.preplit.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.voyageai.VoyageAiEmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuration for embedding models based on AI provider.
 *
 * Provider mapping:
 * - anthropic → voyage-3 (Voyage AI)
 * - openai → text-embedding-3-small (OpenAI)
 * - sarvam → nomic-embed-text (Ollama, local/Docker)
 */
@Configuration
@ConditionalOnProperty(name = "rag.enabled", havingValue = "true", matchIfMissing = true)
public class EmbeddingConfig {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingConfig.class);

    @Bean
    public EmbeddingModel embeddingModel(
            @Value("${ai.provider}") String provider,
            @Value("${ai.api-key}") String aiApiKey,
            @Value("${embedding.voyage.api-key:}") String voyageApiKey,
            @Value("${embedding.voyage.model:voyage-3}") String voyageModel,
            @Value("${embedding.openai.model:text-embedding-3-small}") String openAiModel,
            @Value("${embedding.ollama.base-url:http://localhost:11434}") String ollamaBaseUrl,
            @Value("${embedding.ollama.model:nomic-embed-text}") String ollamaModel) {

        return switch (provider.toLowerCase()) {
            case "anthropic" -> {
                String apiKey = voyageApiKey.isBlank() ? aiApiKey : voyageApiKey;
                log.info("Initializing Voyage AI embedding model: {}", voyageModel);
                yield VoyageAiEmbeddingModel.builder()
                        .apiKey(apiKey)
                        .modelName(voyageModel)
                        .timeout(Duration.ofSeconds(60))
                        .build();
            }
            case "openai" -> {
                log.info("Initializing OpenAI embedding model: {}", openAiModel);
                yield OpenAiEmbeddingModel.builder()
                        .apiKey(aiApiKey)
                        .modelName(openAiModel)
                        .timeout(Duration.ofSeconds(60))
                        .build();
            }
            case "sarvam" -> {
                log.info("Initializing Ollama embedding model: {} at {}", ollamaModel, ollamaBaseUrl);
                yield OllamaEmbeddingModel.builder()
                        .baseUrl(ollamaBaseUrl)
                        .modelName(ollamaModel)
                        .timeout(Duration.ofSeconds(120))
                        .build();
            }
            default -> throw new IllegalStateException("Unsupported AI provider for embeddings: " + provider);
        };
    }

    /**
     * Returns the embedding dimension for the configured provider.
     * Used when creating Redis vector index.
     */
    @Bean
    public int embeddingDimension(@Value("${ai.provider}") String provider) {
        return switch (provider.toLowerCase()) {
            case "anthropic" -> 1024;   // voyage-3: 1024 dimensions
            case "openai" -> 1536;      // text-embedding-3-small: 1536 dimensions
            case "sarvam" -> 768;       // nomic-embed-text: 768 dimensions
            default -> 768;
        };
    }
}
