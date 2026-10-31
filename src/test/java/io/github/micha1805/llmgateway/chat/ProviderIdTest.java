package io.github.micha1805.llmgateway.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ProviderIdTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serializesWireNames() throws JsonProcessingException {
        assertThat(mapper.writeValueAsString(ProviderId.GROK)).isEqualTo("\"grok\"");
        assertThat(mapper.readValue("\"OpenAI\"", ProviderId.class)).isEqualTo(ProviderId.OPENAI);
    }

    @Test
    void rejectsUnknownProviders() {
        assertThatThrownBy(() -> mapper.readValue("\"gemini\"", ProviderId.class))
                .hasMessageContaining("Unknown provider 'gemini'")
                .hasMessageContaining("openai, anthropic, grok, mistral, ollama");
    }

    @Test
    void blankJsonValueBecomesNullSoBeanValidationCanExplainIt() throws JsonProcessingException {
        assertThat(mapper.readValue("\"  \"", ProviderId.class)).isNull();
    }

    @Test
    void registryRequiresEveryProvider() {
        assertThatThrownBy(() -> new ProviderRegistry(List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Expected 5");
    }
}
