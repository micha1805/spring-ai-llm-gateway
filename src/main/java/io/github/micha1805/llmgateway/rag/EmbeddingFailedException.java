package io.github.micha1805.llmgateway.rag;

public final class EmbeddingFailedException extends RuntimeException {

    public EmbeddingFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
