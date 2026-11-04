package io.github.micha1805.llmgateway.rag;

public final class BadDocumentException extends RuntimeException {

    public BadDocumentException(String message) {
        super(message);
    }
}
