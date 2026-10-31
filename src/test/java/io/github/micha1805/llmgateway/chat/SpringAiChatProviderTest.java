package io.github.micha1805.llmgateway.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

@ExtendWith(MockitoExtension.class)
class SpringAiChatProviderTest {

    @Mock
    private ChatModel chatModel;

    @Test
    void chatClientReturnsTheModelTextAndReportsTheOverride() {
        when(chatModel.getDefaultOptions()).thenReturn(OpenAiChatOptions.builder().model("gpt-4o-mini").build());
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage("Virtual threads share carrier threads."))),
                ChatResponseMetadata.builder().model("gpt-4o").build()));

        SpringAiChatProvider provider = provider();
        ChatCompletion completion = provider.complete("Explain virtual threads", "gpt-4o");

        assertThat(completion.model()).isEqualTo("gpt-4o");
        assertThat(completion.answer()).isEqualTo("Virtual threads share carrier threads.");

        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        assertThat(prompt.getValue().getOptions().getModel()).isEqualTo("gpt-4o");
        assertThat(prompt.getValue().getInstructions().getFirst().getText()).isEqualTo("Explain virtual threads");
    }

    @Test
    void fallsBackToTheRequestedModelWhenMetadataOmitsIt() {
        when(chatModel.getDefaultOptions()).thenReturn(OpenAiChatOptions.builder().model("gpt-4o-mini").build());
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage("Hello")))));

        ChatCompletion completion = provider().complete("Hi", null);

        assertThat(completion.model()).isEqualTo("gpt-4o-mini");
        assertThat(completion.answer()).isEqualTo("Hello");
    }

    @Test
    void emptyAnswersAreRejected() {
        when(chatModel.getDefaultOptions()).thenReturn(OpenAiChatOptions.builder().model("gpt-4o-mini").build());
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage("   ")))));

        assertThatThrownBy(() -> provider().complete("Hi", null))
                .isInstanceOf(EmptyAnswerException.class)
                .hasMessageContaining("openai");
    }

    private SpringAiChatProvider provider() {
        return new SpringAiChatProvider(
                ProviderId.OPENAI,
                "gpt-4o-mini",
                "https://api.openai.com",
                org.springframework.ai.chat.client.ChatClient.builder(chatModel).build(),
                requested -> OpenAiChatOptions.builder().model(requested).build());
    }
}
