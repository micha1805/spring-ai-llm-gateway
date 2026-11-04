package io.github.micha1805.llmgateway.api;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "Source", description = "A retrieved chunk cited with the answer")
public record SourceCitation(
        @Schema(description = "Ingested document that contained this chunk",
                example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID documentId,

        @Schema(description = "File name stored at ingestion", example = "hospitalisation-fr.pdf")
        String documentName,

        @Schema(description = "1-based PDF page. Omitted for text and Markdown.", example = "1", nullable = true)
        Integer page,

        @Schema(description = "Zero-based chunk index within the document", example = "0")
        int chunk,

        @Schema(description = "Short extract of the chunk",
                example = "Forfait journalier fictif: 47 euros par jour, maximum 30 jours par annee civile.")
        String snippet,

        @Schema(description = "Cosine similarity in the range -1 to 1. Higher means closer to the question.",
                example = "0.7421")
        double score
) {
}
