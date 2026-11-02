package io.github.micha1805.llmgateway.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "llm")
public record LlmGatewayProperties(Http http, Providers providers) {

    public Http httpOrDefault() {
        if (http == null) {
            return new Http(Duration.ofSeconds(5), Duration.ofSeconds(120));
        }
        return http;
    }

    public record Http(
            @DefaultValue("5s") Duration connectTimeout,
            @DefaultValue("120s") Duration readTimeout) {
    }

    public record Providers(Remote openai, Remote anthropic, Remote grok, Remote mistral, Ollama ollama) {
    }

    public record Remote(String apiKey, String baseUrl, String model, Integer maxTokens) {
    }

    public record Ollama(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("http://localhost:11434") String baseUrl,
            @DefaultValue("llama3.2") String model) {
    }
}
