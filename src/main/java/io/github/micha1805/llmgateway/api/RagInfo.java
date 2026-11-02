package io.github.micha1805.llmgateway.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "RagStatus", description = "Whether retrieval is configured. API keys are never returned.")
public record RagInfo(
        @Schema(description = "True when documents can be embedded and searched", example = "false")
        boolean available,

        @Schema(description = "Embedding provider. Independent of the chat provider on each request.",
                example = "transformers")
        String embeddingProvider,

        @Schema(description = "Embedding model id", example = "paraphrase-multilingual-MiniLM-L12-v2")
        String embeddingModel,

        @Schema(description = "Vector width expected by the store", example = "384")
        int dimensions,

        @Schema(description = "Vector store implementation", example = "pgvector")
        String vectorStore,

        @Schema(description = "Why retrieval cannot be used. Omitted when it is available.",
                example = "Document store is not available. Set SPRING_DATASOURCE_URL to a PostgreSQL database with the pgvector extension.")
        String reason
) {
}
