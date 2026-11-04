package io.github.micha1805.llmgateway.config;

import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "llm.embeddings")
public record EmbeddingProperties(
        @DefaultValue("transformers") String provider,
        Integer dimensions,
        OpenAi openai,
        Ollama ollama,
        Transformers transformers) {

    private static final Set<String> PROVIDERS = Set.of("transformers", "openai", "ollama", "deterministic");

    public EmbeddingProperties {
        String normalized = provider == null || provider.isBlank() ? "transformers" : provider.trim().toLowerCase();
        if (!PROVIDERS.contains(normalized)) {
            throw new IllegalStateException(
                    "llm.embeddings.provider must be transformers, openai, or ollama.");
        }
        if (dimensions != null && dimensions < 1) {
            throw new IllegalStateException("llm.embeddings.dimensions must be at least 1.");
        }
        if ("transformers".equals(normalized) && dimensions != null && dimensions != 384) {
            throw new IllegalStateException(
                    "The local ONNX model produces 384 dimensions. Leave EMBEDDING_DIMENSIONS unset or set it to 384.");
        }
    }

    public String normalizedProvider() {
        if (provider == null || provider.isBlank()) {
            return "transformers";
        }
        return provider.trim().toLowerCase();
    }

    public int resolvedDimensions() {
        if (dimensions != null && dimensions > 0) {
            return dimensions;
        }
        return switch (normalizedProvider()) {
            case "openai" -> 1536;
            case "ollama" -> 1024;
            default -> 384;
        };
    }

    public String modelName() {
        return switch (normalizedProvider()) {
            case "openai" -> text(openai == null ? null : openai.model(), "text-embedding-3-small");
            case "ollama" -> text(ollama == null ? null : ollama.model(), "bge-m3");
            case "deterministic" -> "deterministic-hash";
            default -> "paraphrase-multilingual-MiniLM-L12-v2";
        };
    }

    private static String text(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    public record OpenAi(
            String apiKey,
            @DefaultValue("https://api.openai.com") String baseUrl,
            @DefaultValue("text-embedding-3-small") String model) {
    }

    public record Ollama(
            @DefaultValue("http://localhost:11434") String baseUrl,
            @DefaultValue("bge-m3") String model) {
    }

    public record Transformers(
            @DefaultValue("https://huggingface.co/Xenova/paraphrase-multilingual-MiniLM-L12-v2/resolve/main/onnx/model_quantized.onnx")
            String modelUri,
            @DefaultValue("https://huggingface.co/Xenova/paraphrase-multilingual-MiniLM-L12-v2/resolve/main/tokenizer.json")
            String tokenizerUri,
            @DefaultValue("last_hidden_state") String modelOutputName,
            @DefaultValue("${java.io.tmpdir}/llm-gateway-onnx") String cacheDirectory) {
    }
}
