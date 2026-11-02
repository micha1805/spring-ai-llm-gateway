package io.github.micha1805.llmgateway.rag;

import io.github.micha1805.llmgateway.api.DocumentSummary;

public record IngestResult(DocumentSummary document, boolean created) {
}
