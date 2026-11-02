package io.github.micha1805.llmgateway.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * True when a PostgreSQL URL is configured. RAG beans stay out of the context otherwise,
 * so {@code /api/chat} still starts with no database.
 */
public final class DatabaseConfigured implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return StringUtils.hasText(context.getEnvironment().getProperty("spring.datasource.url"));
    }
}
