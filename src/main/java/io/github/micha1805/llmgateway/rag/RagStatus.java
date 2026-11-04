package io.github.micha1805.llmgateway.rag;

import java.util.concurrent.atomic.AtomicReference;

import io.github.micha1805.llmgateway.api.RagInfo;
import io.github.micha1805.llmgateway.config.EmbeddingProperties;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RagStatus {

    public static final String NO_DATABASE = "Document store is not available. Set SPRING_DATASOURCE_URL to a PostgreSQL database with the pgvector extension.";

    public static final String NO_OPENAI_KEY = "Embeddings are not available. Set OPENAI_API_KEY, or set EMBEDDING_PROVIDER to transformers (local ONNX, no key) or ollama.";

    private final Environment environment;

    private final EmbeddingProperties embeddings;

    private final ObjectProvider<VectorStore> vectorStore;

    private final AtomicReference<String> runtimeProblem = new AtomicReference<>();

    public RagStatus(Environment environment, EmbeddingProperties embeddings, ObjectProvider<VectorStore> vectorStore) {
        this.environment = environment;
        this.embeddings = embeddings;
        this.vectorStore = vectorStore;
    }

    public boolean available() {
        return runtimeProblem.get() == null && vectorStore.getIfAvailable() != null;
    }

    public String reason() {
        String problem = runtimeProblem.get();
        if (problem != null) {
            return problem;
        }
        if (vectorStore.getIfAvailable() != null) {
            return null;
        }
        if (!StringUtils.hasText(environment.getProperty("spring.datasource.url"))) {
            return NO_DATABASE;
        }
        if ("openai".equals(embeddings.normalizedProvider())
                && !StringUtils.hasText(environment.getProperty("llm.embeddings.openai.api-key"))) {
            return NO_OPENAI_KEY;
        }
        return "Embeddings are not available for provider '" + embeddings.normalizedProvider() + "'.";
    }

    public void requireAvailable() {
        if (!available()) {
            throw new RagUnavailableException(reason());
        }
    }

    public void markUnavailable(String reason) {
        runtimeProblem.set(reason);
    }

    public RagInfo info() {
        return new RagInfo(
                available(),
                embeddings.normalizedProvider(),
                embeddings.modelName(),
                embeddings.resolvedDimensions(),
                "pgvector",
                reason());
    }
}
