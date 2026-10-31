package io.github.micha1805.llmgateway.chat;

import java.util.List;

import io.github.micha1805.llmgateway.api.ChatReply;
import io.github.micha1805.llmgateway.api.ChatRequest;
import io.github.micha1805.llmgateway.api.ProviderInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ProviderRegistry registry;

    public ChatService(ProviderRegistry registry) {
        this.registry = registry;
    }

    public ChatReply chat(ChatRequest request) {
        ChatProvider provider = registry.requireAvailable(request.provider());
        String model = request.model() == null ? provider.defaultModel() : request.model();
        log.info("Chat request provider={} model={}", provider.id().id(), model);
        try {
            ChatCompletion completion = provider.complete(request.message(), request.model());
            log.info("Chat completed provider={} model={} characters={}",
                    provider.id().id(), completion.model(), completion.answer().length());
            return new ChatReply(provider.id(), completion.model(), completion.answer());
        }
        catch (ProviderUnavailableException | EmptyAnswerException exception) {
            throw exception;
        }
        catch (RuntimeException exception) {
            String detail = upstreamDetail(provider.id(), exception);
            log.warn("Chat failed provider={} model={} reason={}", provider.id().id(), model, detail, exception);
            throw new UpstreamModelException(provider.id(), detail, exception);
        }
    }

    public List<ProviderInfo> providers() {
        return registry.all().stream().map(ChatService::toInfo).toList();
    }

    private static ProviderInfo toInfo(ChatProvider provider) {
        return new ProviderInfo(
                provider.id(),
                provider.id().displayName(),
                provider.available(),
                provider.defaultModel(),
                provider.baseUrl(),
                provider.id().configurationEnv(),
                provider.unavailableReason());
    }

    private static String upstreamDetail(ProviderId id, Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ResourceAccessException) {
                return "Could not reach provider '" + id.id()
                        + "'. Check its base URL and that the service is running.";
            }
            if (current instanceof RestClientResponseException response) {
                return "Provider '" + id.id() + "' returned HTTP " + response.getStatusCode().value() + ".";
            }
            current = current.getCause();
        }
        return "Provider '" + id.id() + "' failed to produce an answer.";
    }
}
