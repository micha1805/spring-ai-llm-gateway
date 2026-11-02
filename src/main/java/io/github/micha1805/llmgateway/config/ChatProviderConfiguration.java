package io.github.micha1805.llmgateway.config;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

import io.github.micha1805.llmgateway.chat.ChatProvider;
import io.github.micha1805.llmgateway.chat.ProviderId;
import io.github.micha1805.llmgateway.chat.ProviderRegistry;
import io.github.micha1805.llmgateway.chat.SpringAiChatProvider;
import io.github.micha1805.llmgateway.chat.UnavailableChatProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mistralai.MistralAiChatModel;
import org.springframework.ai.mistralai.MistralAiChatOptions;
import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.retry.policy.MaxAttemptsRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
class ChatProviderConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ChatProviderConfiguration.class);

    private static final int DEFAULT_ANTHROPIC_MAX_TOKENS = 1024;

    @Bean
    ProviderRegistry providerRegistry(LlmGatewayProperties properties) {
        LlmGatewayProperties.Http http = properties.httpOrDefault();
        requirePositive(http.connectTimeout(), "llm.http.connect-timeout");
        requirePositive(http.readTimeout(), "llm.http.read-timeout");

        RestClient.Builder restClientBuilder = restClientBuilder(http.connectTimeout(), http.readTimeout());
        WebClient.Builder webClientBuilder = WebClient.builder();
        RetryTemplate retryTemplate = singleAttempt();
        LlmGatewayProperties.Providers providers = requireProviders(properties);

        ProviderRegistry registry = new ProviderRegistry(List.of(
                openAiCompatible(ProviderId.OPENAI, providers.openai(), restClientBuilder, webClientBuilder, retryTemplate),
                anthropic(providers.anthropic(), restClientBuilder, webClientBuilder, retryTemplate),
                openAiCompatible(ProviderId.GROK, providers.grok(), restClientBuilder, webClientBuilder, retryTemplate),
                mistral(providers.mistral(), restClientBuilder, webClientBuilder, retryTemplate),
                ollama(providers.ollama(), restClientBuilder, webClientBuilder, retryTemplate)));

        long available = registry.all().stream().filter(ChatProvider::available).count();
        log.info("LLM providers ready: {} of {}", available, registry.all().size());
        for (ChatProvider provider : registry.all()) {
            log.info("Provider {} available={} model={} baseUrl={}",
                    provider.id().id(), provider.available(), provider.defaultModel(), provider.baseUrl());
        }
        return registry;
    }

    private static ChatProvider openAiCompatible(ProviderId id, LlmGatewayProperties.Remote settings,
            RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder, RetryTemplate retryTemplate) {
        requireSettings(id, settings);
        String baseUrl = requireText(id, "base-url", settings.baseUrl());
        String model = requireText(id, "model", settings.model());
        if (!StringUtils.hasText(settings.apiKey())) {
            return UnavailableChatProvider.missingKey(id, model, baseUrl);
        }
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(settings.apiKey().trim())
                .restClientBuilder(restClientBuilder)
                .webClientBuilder(webClientBuilder)
                .build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model(model).build())
                .retryTemplate(retryTemplate)
                .build();
        return new SpringAiChatProvider(id, model, baseUrl, ChatClient.builder(chatModel).build(),
                requested -> OpenAiChatOptions.builder().model(requested).build());
    }

    private static ChatProvider anthropic(LlmGatewayProperties.Remote settings, RestClient.Builder restClientBuilder,
            WebClient.Builder webClientBuilder, RetryTemplate retryTemplate) {
        ProviderId id = ProviderId.ANTHROPIC;
        requireSettings(id, settings);
        String baseUrl = requireText(id, "base-url", settings.baseUrl());
        String model = requireText(id, "model", settings.model());
        if (!StringUtils.hasText(settings.apiKey())) {
            return UnavailableChatProvider.missingKey(id, model, baseUrl);
        }
        int maxTokens = settings.maxTokens() == null ? DEFAULT_ANTHROPIC_MAX_TOKENS : settings.maxTokens();
        if (maxTokens < 1) {
            throw new IllegalStateException("llm.providers.anthropic.max-tokens must be at least 1.");
        }
        AnthropicApi api = AnthropicApi.builder()
                .baseUrl(baseUrl)
                .apiKey(settings.apiKey().trim())
                .restClientBuilder(restClientBuilder)
                .webClientBuilder(webClientBuilder)
                .build();
        AnthropicChatModel chatModel = AnthropicChatModel.builder()
                .anthropicApi(api)
                .defaultOptions(AnthropicChatOptions.builder().model(model).maxTokens(maxTokens).build())
                .retryTemplate(retryTemplate)
                .build();
        return new SpringAiChatProvider(id, model, baseUrl, ChatClient.builder(chatModel).build(),
                requested -> AnthropicChatOptions.builder().model(requested).maxTokens(maxTokens).build());
    }

    private static ChatProvider mistral(LlmGatewayProperties.Remote settings, RestClient.Builder restClientBuilder,
            WebClient.Builder webClientBuilder, RetryTemplate retryTemplate) {
        ProviderId id = ProviderId.MISTRAL;
        requireSettings(id, settings);
        String baseUrl = requireText(id, "base-url", settings.baseUrl());
        String model = requireText(id, "model", settings.model());
        if (!StringUtils.hasText(settings.apiKey())) {
            return UnavailableChatProvider.missingKey(id, model, baseUrl);
        }
        MistralAiApi api = MistralAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(settings.apiKey().trim())
                .restClientBuilder(restClientBuilder)
                .webClientBuilder(webClientBuilder)
                .build();
        MistralAiChatModel chatModel = MistralAiChatModel.builder()
                .mistralAiApi(api)
                .defaultOptions(MistralAiChatOptions.builder().model(model).build())
                .retryTemplate(retryTemplate)
                .build();
        return new SpringAiChatProvider(id, model, baseUrl, ChatClient.builder(chatModel).build(),
                requested -> MistralAiChatOptions.builder().model(requested).build());
    }

    private static ChatProvider ollama(LlmGatewayProperties.Ollama settings, RestClient.Builder restClientBuilder,
            WebClient.Builder webClientBuilder, RetryTemplate retryTemplate) {
        ProviderId id = ProviderId.OLLAMA;
        if (settings == null) {
            throw new IllegalStateException("llm.providers.ollama is required.");
        }
        String baseUrl = requireText(id, "base-url", settings.baseUrl());
        String model = requireText(id, "model", settings.model());
        if (!settings.enabled()) {
            return UnavailableChatProvider.disabled(id, model, baseUrl,
                    "Provider 'ollama' is disabled. Set OLLAMA_ENABLED=true to enable it.");
        }
        OllamaApi api = OllamaApi.builder()
                .baseUrl(baseUrl)
                .restClientBuilder(restClientBuilder)
                .webClientBuilder(webClientBuilder)
                .build();
        OllamaChatModel chatModel = OllamaChatModel.builder()
                .ollamaApi(api)
                .defaultOptions(OllamaChatOptions.builder().model(model).build())
                .retryTemplate(retryTemplate)
                .build();
        return new SpringAiChatProvider(id, model, baseUrl, ChatClient.builder(chatModel).build(),
                requested -> OllamaChatOptions.builder().model(requested).build());
    }

    private static RestClient.Builder restClientBuilder(Duration connectTimeout, Duration readTimeout) {
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

    private static LlmGatewayProperties.Providers requireProviders(LlmGatewayProperties properties) {
        if (properties.providers() == null) {
            throw new IllegalStateException("llm.providers is required.");
        }
        return properties.providers();
    }

    private static void requireSettings(ProviderId id, LlmGatewayProperties.Remote settings) {
        if (settings == null) {
            throw new IllegalStateException("llm.providers." + id.id() + " is required.");
        }
    }

    private static String requireText(ProviderId id, String field, String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("llm.providers." + id.id() + "." + field + " must not be blank.");
        }
        return value.trim();
    }

    private static void requirePositive(Duration duration, String property) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalStateException(property + " must be positive.");
        }
    }
}
