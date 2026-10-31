package io.github.micha1805.llmgateway.chat;

public final class ProviderUnavailableException extends RuntimeException {

    private final ProviderId provider;

    public ProviderUnavailableException(ProviderId provider, String message) {
        super(message);
        this.provider = provider;
    }

    public ProviderId provider() {
        return provider;
    }
}
