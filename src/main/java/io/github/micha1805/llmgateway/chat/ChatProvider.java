package io.github.micha1805.llmgateway.chat;

public interface ChatProvider {

    ProviderId id();

    String defaultModel();

    String baseUrl();

    boolean available();

    String unavailableReason();

    ChatCompletion complete(String message, String modelOverride);
}
