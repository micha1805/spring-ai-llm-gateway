package io.github.micha1805.llmgateway.chat;

public final class UnavailableChatProvider implements ChatProvider {

    private final ProviderId id;

    private final String defaultModel;

    private final String baseUrl;

    private final String reason;

    private UnavailableChatProvider(ProviderId id, String defaultModel, String baseUrl, String reason) {
        this.id = id;
        this.defaultModel = defaultModel;
        this.baseUrl = baseUrl;
        this.reason = reason;
    }

    public static UnavailableChatProvider missingKey(ProviderId id, String defaultModel, String baseUrl) {
        return new UnavailableChatProvider(id, defaultModel, baseUrl,
                "Provider '" + id.id() + "' is not available. Set the " + id.configurationEnv()
                        + " environment variable.");
    }

    public static UnavailableChatProvider disabled(ProviderId id, String defaultModel, String baseUrl, String reason) {
        return new UnavailableChatProvider(id, defaultModel, baseUrl, reason);
    }

    @Override
    public ProviderId id() {
        return id;
    }

    @Override
    public String defaultModel() {
        return defaultModel;
    }

    @Override
    public String baseUrl() {
        return baseUrl;
    }

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public String unavailableReason() {
        return reason;
    }

    @Override
    public ChatCompletion complete(String message, String modelOverride) {
        throw new ProviderUnavailableException(id, reason);
    }
}
