package io.github.micha1805.llmgateway.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.micha1805.llmgateway.chat.ChatService;
import io.github.micha1805.llmgateway.chat.ProviderId;
import io.github.micha1805.llmgateway.chat.ProviderUnavailableException;
import io.github.micha1805.llmgateway.chat.UpstreamModelException;
import io.github.micha1805.llmgateway.error.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.mockito.ArgumentCaptor;

@WebMvcTest(controllers = ChatController.class)
@Import(ApiExceptionHandler.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService chatService;

    @Test
    void returnsTheChatReply() throws Exception {
        when(chatService.chat(any())).thenReturn(
                new ChatReply(ProviderId.GROK, "grok-4.7", "Virtual threads are lightweight."));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"grok","message":"  Explain virtual threads  ","model":" grok-4.7 "}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.provider").value("grok"))
                .andExpect(jsonPath("$.model").value("grok-4.7"))
                .andExpect(jsonPath("$.answer").value("Virtual threads are lightweight."));

        ArgumentCaptor<ChatRequest> request = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatService).chat(request.capture());
        org.assertj.core.api.Assertions.assertThat(request.getValue().message()).isEqualTo("Explain virtual threads");
        org.assertj.core.api.Assertions.assertThat(request.getValue().model()).isEqualTo("grok-4.7");
        org.assertj.core.api.Assertions.assertThat(request.getValue().provider()).isEqualTo(ProviderId.GROK);
    }

    @Test
    void rejectsAnEmptyMessage() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"openai","message":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.errors[0].field").value("message"))
                .andExpect(jsonPath("$.errors[0].message").value("message is required"));
    }

    @Test
    void rejectsAnUnknownProvider() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"gemini","message":"Hello"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"))
                .andExpect(jsonPath("$.detail").value(
                        "Unknown provider 'gemini'. Expected one of: openai, anthropic, grok, mistral, ollama."));
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"));
    }

    @Test
    void reportsAnUnconfiguredProviderAs503() throws Exception {
        when(chatService.chat(any())).thenThrow(new ProviderUnavailableException(ProviderId.ANTHROPIC,
                "Provider 'anthropic' is not available. Set the ANTHROPIC_API_KEY environment variable."));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"anthropic","message":"Hello"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Provider not available"))
                .andExpect(jsonPath("$.provider").value("anthropic"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("ANTHROPIC_API_KEY")));
    }

    @Test
    void reportsAnUpstreamFailureAs502() throws Exception {
        when(chatService.chat(any())).thenThrow(new UpstreamModelException(
                ProviderId.MISTRAL, "Provider 'mistral' returned HTTP 429.", new IllegalStateException("rate limit")));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"mistral","message":"Hello"}
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.title").value("Provider request failed"))
                .andExpect(jsonPath("$.provider").value("mistral"))
                .andExpect(jsonPath("$.detail").value("Provider 'mistral' returned HTTP 429."))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("rate limit"))));
    }
}
