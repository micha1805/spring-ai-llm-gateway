package io.github.micha1805.llmgateway.api;

import io.github.micha1805.llmgateway.chat.ProviderId;
import io.github.micha1805.llmgateway.config.RagProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "AskRequest", description = "A question answered only from ingested documents")
public record AskRequest(
        @NotBlank(message = "question is required")
        @Size(max = ApiConstraints.MAX_MESSAGE_LENGTH, message = "question must be at most 32000 characters")
        @Schema(description = "Question. French, Dutch, and English are supported. The answer uses the question's language.",
                example = "Quel est le forfait journalier fictif de la garantie hospitalisation ?",
                maxLength = ApiConstraints.MAX_MESSAGE_LENGTH)
        String question,

        @NotNull(message = "provider is required")
        @Schema(description = "Chat provider that writes the answer. Embeddings stay on the configured embedding model.",
                example = "grok")
        ProviderId provider,

        @Size(max = ApiConstraints.MAX_MODEL_LENGTH, message = "model must be at most 128 characters")
        @Schema(description = "Optional chat model id. When omitted, the provider's configured default is used.",
                example = "grok-4.7", nullable = true, maxLength = ApiConstraints.MAX_MODEL_LENGTH)
        String model,

        @Min(value = 1, message = "topK must be at least 1")
        @Max(value = RagProperties.MAX_TOP_K, message = "topK must be at most 10")
        @Schema(description = "How many chunks to retrieve. Defaults to the configured RAG_TOP_K (4).",
                example = "4", nullable = true, minimum = "1", maximum = "10")
        Integer topK
) {

    public AskRequest {
        question = question == null ? null : question.strip();
        if (model != null) {
            model = model.strip();
            if (model.isEmpty()) {
                model = null;
            }
        }
    }
}
