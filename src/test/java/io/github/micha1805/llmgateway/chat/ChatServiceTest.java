package io.github.micha1805.llmgateway.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.EnumMap;
import java.util.Map;

import io.github.micha1805.llmgateway.api.ChatReply;
import io.github.micha1805.llmgateway.api.ChatRequest;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

class ChatServiceTest {

    @Test
    void returnsTheProviderAnswer() {
        ChatProvider openai = available(ProviderId.OPENAI, "gpt-4o-mini");
        when(openai.complete("Explain virtual threads", null))
                .thenReturn(new ChatCompletion("gpt-4o-mini", "They are lightweight threads."));

        ChatReply reply = service(openai).chat(new ChatRequest("Explain virtual threads", ProviderId.OPENAI, null));

        assertThat(reply.provider()).isEqualTo(ProviderId.OPENAI);
        assertThat(reply.model()).isEqualTo("gpt-4o-mini");
        assertThat(reply.answer()).isEqualTo("They are lightweight threads.");
    }

    @Test
    void missingKeyIsAServiceUnavailableError() {
        ChatService service = service(available(ProviderId.OLLAMA, "llama3.2"));

        assertThatThrownBy(() -> service.chat(new ChatRequest("Hello", ProviderId.OPENAI, null)))
                .isInstanceOf(ProviderUnavailableException.class)
                .hasMessageContaining("OPENAI_API_KEY");
    }

    @Test
    void connectionFailuresMentionTheProviderWithoutThePrompt() {
        ChatProvider ollama = available(ProviderId.OLLAMA, "llama3.2");
        when(ollama.complete(any(), any())).thenThrow(new ResourceAccessException("Connection refused"));

        assertThatThrownBy(() -> service(ollama).chat(new ChatRequest("Hello", ProviderId.OLLAMA, null)))
                .isInstanceOf(UpstreamModelException.class)
                .hasMessageContaining("Could not reach provider 'ollama'")
                .hasMessageNotContaining("Hello");
    }

    @Test
    void httpErrorsKeepOnlyTheStatusCode() {
        ChatProvider openai = available(ProviderId.OPENAI, "gpt-4o-mini");
        when(openai.complete(any(), any())).thenThrow(HttpClientErrorException.create(
                HttpStatus.UNAUTHORIZED, "Unauthorized", HttpHeaders.EMPTY,
                "sk-secret-body".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service(openai).chat(new ChatRequest("Hello", ProviderId.OPENAI, "gpt-4o")))
                .isInstanceOf(UpstreamModelException.class)
                .hasMessageContaining("HTTP 401")
                .hasMessageNotContaining("sk-secret-body");
    }

    private static ChatService service(ChatProvider configured) {
        Map<ProviderId, ChatProvider> providers = new EnumMap<>(ProviderId.class);
        for (ProviderId id : ProviderId.values()) {
            providers.put(id, UnavailableChatProvider.missingKey(id, "default", "https://example.invalid"));
        }
        providers.put(configured.id(), configured);
        return new ChatService(new ProviderRegistry(providers.values()));
    }

    private static ChatProvider available(ProviderId id, String model) {
        ChatProvider provider = mock(ChatProvider.class);
        when(provider.id()).thenReturn(id);
        when(provider.available()).thenReturn(true);
        when(provider.defaultModel()).thenReturn(model);
        when(provider.baseUrl()).thenReturn("https://example.invalid");
        when(provider.unavailableReason()).thenReturn(null);
        return provider;
    }
}
