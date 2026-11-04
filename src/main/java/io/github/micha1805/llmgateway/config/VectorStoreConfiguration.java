package io.github.micha1805.llmgateway.config;

import org.flywaydb.core.Flyway;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
class VectorStoreConfiguration {

    @Bean
    @Conditional(EmbeddingConfigured.class)
    PgVectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel, EmbeddingProperties properties,
            Flyway flyway) {
        // The parameter forces Flyway to finish, including the catalog migration, before the
        // vector table is created. Flyway does not create vector_store; PgVectorStore does.
        flyway.info();
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(properties.resolvedDimensions())
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .initializeSchema(true)
                .schemaName("public")
                .vectorTableName("vector_store")
                .maxDocumentBatchSize(1000)
                .build();
    }
}
