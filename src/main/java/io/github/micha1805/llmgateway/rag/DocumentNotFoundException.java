package io.github.micha1805.llmgateway.rag;

import java.util.UUID;

public final class DocumentNotFoundException extends RuntimeException {

    private final UUID documentId;

    public DocumentNotFoundException(UUID documentId) {
        super("Document '" + documentId + "' was not found.");
        this.documentId = documentId;
    }

    public UUID documentId() {
        return documentId;
    }
}
