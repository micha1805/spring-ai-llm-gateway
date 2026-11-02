package io.github.micha1805.llmgateway.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * True when the vector store can be built: a database URL is set, and the chosen
 * embedding provider does not need a missing key. OpenAI embeddings are the only
 * provider that needs a key. xAI is not an embedding provider.
 */
public final class EmbeddingConfigured implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        if (!StringUtils.hasText(context.getEnvironment().getProperty("spring.datasource.url"))) {
            return false;
        }
        String provider = context.getEnvironment().getProperty("llm.embeddings.provider", "transformers");
        if (provider == null || provider.isBlank()) {
            provider = "transformers";
        }
        provider = provider.trim().toLowerCase();
        if ("openai".equals(provider)) {
            return StringUtils.hasText(context.getEnvironment().getProperty("llm.embeddings.openai.api-key"));
        }
        return "transformers".equals(provider) || "ollama".equals(provider) || "deterministic".equals(provider);
    }
}
