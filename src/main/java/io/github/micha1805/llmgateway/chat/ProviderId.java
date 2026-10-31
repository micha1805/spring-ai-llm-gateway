package io.github.micha1805.llmgateway.chat;

import java.util.Arrays;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "LLM provider selected for this request", example = "grok")
public enum ProviderId {

    OPENAI("openai", "OpenAI", "OPENAI_API_KEY"),
    ANTHROPIC("anthropic", "Anthropic", "ANTHROPIC_API_KEY"),
    GROK("grok", "xAI Grok", "XAI_API_KEY"),
    MISTRAL("mistral", "Mistral", "MISTRAL_API_KEY"),
    OLLAMA("ollama", "Ollama", "OLLAMA_BASE_URL");

    private final String id;

    private final String displayName;

    private final String configurationEnv;

    ProviderId(String id, String displayName, String configurationEnv) {
        this.id = id;
        this.displayName = displayName;
        this.configurationEnv = configurationEnv;
    }

    @JsonValue
    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String configurationEnv() {
        return configurationEnv;
    }

    public static String supported() {
        return Arrays.stream(values()).map(ProviderId::id).collect(Collectors.joining(", "));
    }

    @JsonCreator
    public static ProviderId fromJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        for (ProviderId provider : values()) {
            if (provider.id.equalsIgnoreCase(normalized)) {
                return provider;
            }
        }
        throw new IllegalArgumentException(
                "Unknown provider '" + normalized + "'. Expected one of: " + supported() + ".");
    }
}
