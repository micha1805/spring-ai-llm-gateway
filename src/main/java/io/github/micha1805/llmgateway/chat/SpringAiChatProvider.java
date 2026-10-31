package io.github.micha1805.llmgateway.chat;

import java.util.function.Function;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.util.StringUtils;

/**
 * One configured provider. Calls Spring AI's {@link ChatClient} and never logs the prompt or the API key.
 */
public final class SpringAiChatProvider implements ChatProvider {

    private final ProviderId id;

    private final String defaultModel;

    private final String baseUrl;

    private final ChatClient chatClient;

    private final Function<String, ChatOptions> optionsForModel;

    public SpringAiChatProvider(ProviderId id, String defaultModel, String baseUrl, ChatClient chatClient,
            Function<String, ChatOptions> optionsForModel) {
        this.id = id;
        this.defaultModel = defaultModel;
        this.baseUrl = baseUrl;
        this.chatClient = chatClient;
        this.optionsForModel = optionsForModel;
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
        return true;
    }

    @Override
    public String unavailableReason() {
        return null;
    }

    @Override
    public ChatCompletion complete(String message, String modelOverride) {
        String model = StringUtils.hasText(modelOverride) ? modelOverride.trim() : defaultModel;
        ChatResponse response = chatClient.prompt()
                .user(message)
                .options(optionsForModel.apply(model))
                .call()
                .chatResponse();
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new EmptyAnswerException(id);
        }
        String answer = response.getResult().getOutput().getText();
        if (!StringUtils.hasText(answer)) {
            throw new EmptyAnswerException(id);
        }
        String reported = response.getMetadata() == null ? null : response.getMetadata().getModel();
        String resolvedModel = StringUtils.hasText(reported) ? reported : model;
        return new ChatCompletion(resolvedModel, answer);
    }
}
