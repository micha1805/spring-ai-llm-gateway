package io.github.micha1805.llmgateway.rag;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import io.github.micha1805.llmgateway.config.EmbeddingProperties;

/**
 * PgVectorStore creates {@code vector_store} on startup and will not alter an existing
 * column. A dimension mismatch would fail the first insert with a driver error, so it
 * is reported as a 503 instead.
 */
@Component
@Order(0)
public class VectorSchemaCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(VectorSchemaCheck.class);

    private static final Pattern VECTOR_DIMENSIONS = Pattern.compile("vector\\((\\d+)\\)");

    private final ObjectProvider<JdbcTemplate> jdbcTemplates;

    private final ObjectProvider<VectorStore> vectorStores;

    private final EmbeddingProperties embeddings;

    private final RagStatus status;

    public VectorSchemaCheck(ObjectProvider<JdbcTemplate> jdbcTemplates, ObjectProvider<VectorStore> vectorStores,
            EmbeddingProperties embeddings, RagStatus status) {
        this.jdbcTemplates = jdbcTemplates;
        this.vectorStores = vectorStores;
        this.embeddings = embeddings;
        this.status = status;
    }

    @Override
    public void run(ApplicationArguments args) {
        JdbcTemplate jdbc = jdbcTemplates.getIfAvailable();
        if (jdbc == null || vectorStores.getIfAvailable() == null) {
            log.info("RAG unavailable: {}", status.reason());
            return;
        }
        String columnType = jdbc.query("""
                SELECT format_type(a.atttypid, a.atttypmod)
                FROM pg_attribute a
                JOIN pg_class c ON a.attrelid = c.oid
                JOIN pg_namespace n ON c.relnamespace = n.oid
                WHERE n.nspname = 'public'
                  AND c.relname = 'vector_store'
                  AND a.attname = 'embedding'
                  AND a.attnum > 0
                  AND NOT a.attisdropped
                """, rs -> rs.next() ? rs.getString(1) : null);
        Integer dimensions = dimensionsOf(columnType);
        int expected = embeddings.resolvedDimensions();
        if (dimensions == null || dimensions != expected) {
            String actual = columnType == null ? "missing" : columnType;
            String reason = "The vector_store.embedding column is " + actual
                    + " but the embedding model produces " + expected
                    + " dimensions. Drop table vector_store and re-ingest, or point EMBEDDING_PROVIDER at the model that created it.";
            status.markUnavailable(reason);
            log.error(reason);
            return;
        }
        log.info("RAG ready provider={} model={} dimensions={}",
                embeddings.normalizedProvider(), embeddings.modelName(), expected);
    }

    private static Integer dimensionsOf(String columnType) {
        if (columnType == null) {
            return null;
        }
        Matcher matcher = VECTOR_DIMENSIONS.matcher(columnType);
        if (!matcher.find()) {
            return null;
        }
        return Integer.parseInt(matcher.group(1));
    }
}
