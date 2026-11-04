package io.github.micha1805.llmgateway.api;

import java.util.List;

import io.github.micha1805.llmgateway.chat.ProviderId;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AskResponse", description = "An answer grounded in retrieved chunks, plus those chunks")
public record AskReply(
        @Schema(description = "Chat provider that wrote the answer", example = "grok")
        ProviderId provider,

        @Schema(description = "Model id reported by the provider, or the model that was requested", example = "grok-4.7")
        String model,

        @Schema(description = "Answer written only from the retrieved chunks. Says so when they do not contain the answer.",
                example = "Le forfait journalier fictif est de 47 euros par jour, avec un maximum de 30 jours.")
        String answer,

        @Schema(description = "Chunks retrieved for this question, best match first")
        List<SourceCitation> sources
) {
}
