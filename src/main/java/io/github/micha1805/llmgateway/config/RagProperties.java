package io.github.micha1805.llmgateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "llm.rag")
public record RagProperties(
        @DefaultValue("4") int defaultTopK,
        @DefaultValue("0.10") double similarityThreshold,
        @DefaultValue("120") int chunkSize,
        @DefaultValue("40") int minChunkSizeChars,
        Samples samples) {

    public static final int MAX_TOP_K = 10;

    public RagProperties {
        if (defaultTopK < 1 || defaultTopK > MAX_TOP_K) {
            throw new IllegalStateException("llm.rag.default-top-k must be between 1 and " + MAX_TOP_K + ".");
        }
        if (similarityThreshold < 0 || similarityThreshold > 1) {
            throw new IllegalStateException("llm.rag.similarity-threshold must be between 0 and 1.");
        }
        if (chunkSize < 16) {
            throw new IllegalStateException("llm.rag.chunk-size must be at least 16.");
        }
        if (minChunkSizeChars < 1) {
            throw new IllegalStateException("llm.rag.min-chunk-size-chars must be at least 1.");
        }
    }

    public boolean samplesEnabled() {
        return samples != null && samples.enabled();
    }

    public record Samples(@DefaultValue("false") boolean enabled) {
    }
}
