package io.github.micha1805.llmgateway.chat;

public final class UpstreamModelException extends RuntimeException {

    private final ProviderId provider;

    public UpstreamModelException(ProviderId provider, String message, Throwable cause) {
        super(message, cause);
        this.provider = provider;
    }

    public ProviderId provider() {
        return provider;
    }
}
