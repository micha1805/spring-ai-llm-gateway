package io.github.micha1805.llmgateway.api;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "Document", description = "An ingested document and how it was split")
public record DocumentSummary(
        @Schema(description = "Stable id of the ingested document", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID id,

        @Schema(description = "Original file name", example = "consultations-fr.md")
        String name,

        @Schema(description = "Media type stored with the document", example = "text/markdown")
        String mediaType,

        @Schema(description = "Origin label copied onto each chunk", example = "sample")
        String source,

        @Schema(description = "Size of the uploaded bytes", example = "1840")
        long byteSize,

        @Schema(description = "Number of chunks embedded in the vector store", example = "4")
        int chunkCount,

        @Schema(description = "When the document was first ingested", example = "2026-11-02T10:15:30Z")
        Instant ingestedAt
) {
}
