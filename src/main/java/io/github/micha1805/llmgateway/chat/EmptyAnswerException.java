package io.github.micha1805.llmgateway.chat;

public final class EmptyAnswerException extends RuntimeException {

    private final ProviderId provider;

    public EmptyAnswerException(ProviderId provider) {
        super("Provider '" + provider.id() + "' returned an empty answer.");
        this.provider = provider;
    }

    public ProviderId provider() {
        return provider;
    }
}
