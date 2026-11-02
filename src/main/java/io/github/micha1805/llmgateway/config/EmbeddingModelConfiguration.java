package io.github.micha1805.llmgateway.config;

import java.net.http.HttpClient;
import java.time.Duration;

import io.github.micha1805.llmgateway.rag.LazyTransformersEmbeddingModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.retry.policy.MaxAttemptsRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
class EmbeddingModelConfiguration {

    @Bean
    @Conditional(EmbeddingConfigured.class)
    @ConditionalOnProperty(name = "llm.embeddings.provider", havingValue = "transformers", matchIfMissing = true)
    EmbeddingModel transformersEmbeddingModel(EmbeddingProperties properties) {
        EmbeddingProperties.Transformers settings = properties.transformers();
        if (settings == null || !StringUtils.hasText(settings.modelUri()) || !StringUtils.hasText(settings.tokenizerUri())) {
            throw new IllegalStateException("llm.embeddings.transformers.model-uri and tokenizer-uri are required.");
        }
        return new LazyTransformersEmbeddingModel(
                settings.modelUri().trim(),
                settings.tokenizerUri().trim(),
                text(settings.modelOutputName(), "last_hidden_state"),
                text(settings.cacheDirectory(), System.getProperty("java.io.tmpdir") + "/llm-gateway-onnx"),
                properties.resolvedDimensions());
    }

    @Bean
    @Conditional(EmbeddingConfigured.class)
    @ConditionalOnProperty(name = "llm.embeddings.provider", havingValue = "openai")
    EmbeddingModel openAiEmbeddingModel(EmbeddingProperties properties, LlmGatewayProperties gateway) {
        EmbeddingProperties.OpenAi settings = properties.openai();
        if (settings == null || !StringUtils.hasText(settings.apiKey())) {
            throw new IllegalStateException("OPENAI_API_KEY is required when EMBEDDING_PROVIDER=openai.");
        }
        LlmGatewayProperties.Http http = gateway.httpOrDefault();
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(text(settings.baseUrl(), "https://api.openai.com"))
                .apiKey(settings.apiKey().trim())
                .restClientBuilder(restClient(http.connectTimeout(), http.readTimeout()))
                .webClientBuilder(WebClient.builder())
                .build();
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(text(settings.model(), "text-embedding-3-small"))
                .dimensions(properties.resolvedDimensions())
                .build();
        return new OpenAiEmbeddingModel(api, MetadataMode.NONE, options, singleAttempt());
    }

    @Bean
    @Conditional(EmbeddingConfigured.class)
    @ConditionalOnProperty(name = "llm.embeddings.provider", havingValue = "ollama")
    EmbeddingModel ollamaEmbeddingModel(EmbeddingProperties properties, LlmGatewayProperties gateway) {
        EmbeddingProperties.Ollama settings = properties.ollama();
        if (settings == null || !StringUtils.hasText(settings.baseUrl()) || !StringUtils.hasText(settings.model())) {
            throw new IllegalStateException("llm.embeddings.ollama.base-url and model are required.");
        }
        LlmGatewayProperties.Http http = gateway.httpOrDefault();
        OllamaApi api = OllamaApi.builder()
                .baseUrl(settings.baseUrl().trim())
                .restClientBuilder(restClient(http.connectTimeout(), http.readTimeout()))
                .webClientBuilder(WebClient.builder())
                .build();
        return OllamaEmbeddingModel.builder()
                .ollamaApi(api)
                .defaultOptions(OllamaEmbeddingOptions.builder().model(settings.model().trim()).build())
                .build();
    }

    private static RestClient.Builder restClient(Duration connectTimeout, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(requestFactory);
    }

    private static RetryTemplate singleAttempt() {
        RetryTemplate template = new RetryTemplate();
        template.setRetryPolicy(new MaxAttemptsRetryPolicy(1));
        return template;
    }

    private static String text(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
