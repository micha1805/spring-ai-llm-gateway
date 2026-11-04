package io.github.micha1805.llmgateway.rag;

public final class RagUnavailableException extends RuntimeException {

    public RagUnavailableException(String message) {
        super(message);
    }
}
