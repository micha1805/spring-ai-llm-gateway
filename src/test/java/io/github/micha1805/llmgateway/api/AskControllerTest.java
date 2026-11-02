package io.github.micha1805.llmgateway.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import io.github.micha1805.llmgateway.chat.ProviderId;
import io.github.micha1805.llmgateway.chat.ProviderUnavailableException;
import io.github.micha1805.llmgateway.error.ApiExceptionHandler;
import io.github.micha1805.llmgateway.rag.EmbeddingFailedException;
import io.github.micha1805.llmgateway.rag.RagService;
import io.github.micha1805.llmgateway.rag.RagStatus;
import io.github.micha1805.llmgateway.rag.RagUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AskController.class)
@Import(ApiExceptionHandler.class)
class AskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RagService ragService;

    @MockitoBean
    private RagStatus ragStatus;

    @Test
    void returnsTheAnswerAndSources() throws Exception {
        UUID id = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
        when(ragService.ask(any())).thenReturn(new AskReply(ProviderId.GROK, "grok-4.7",
                "Le forfait est de 47 euros.",
                List.of(new SourceCitation(id, "hospitalisation-fr.pdf", 1, 0, "47 euros", 0.74))));

        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"grok","question":"  Quel est le forfait ?  ","topK":4}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.provider").value("grok"))
                .andExpect(jsonPath("$.answer").value("Le forfait est de 47 euros."))
                .andExpect(jsonPath("$.sources[0].documentName").value("hospitalisation-fr.pdf"))
                .andExpect(jsonPath("$.sources[0].page").value(1))
                .andExpect(jsonPath("$.sources[0].score").value(0.74));
    }

    @Test
    void rejectsAMissingQuestion() throws Exception {
        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"ollama","question":"  "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.errors[0].field").value("question"));
    }

    @Test
    void reportsRetrievalDownAs503() throws Exception {
        when(ragService.ask(any())).thenThrow(new RagUnavailableException(RagStatus.NO_DATABASE));

        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"grok","question":"Bonjour"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Retrieval not available"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("SPRING_DATASOURCE_URL")));
    }

    @Test
    void reportsAMissingChatKeyAs503() throws Exception {
        when(ragService.ask(any())).thenThrow(new ProviderUnavailableException(ProviderId.ANTHROPIC,
                "Provider 'anthropic' is not available. Set the ANTHROPIC_API_KEY environment variable."));

        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"anthropic","question":"Bonjour"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Provider not available"))
                .andExpect(jsonPath("$.provider").value("anthropic"));
    }

    @Test
    void reportsAnEmbeddingFailureAs502() throws Exception {
        when(ragService.ask(any())).thenThrow(new EmbeddingFailedException(
                "The embedding provider returned HTTP 401.", new IllegalStateException("nope")));

        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"openai","question":"Hello"}
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.title").value("Embedding request failed"))
                .andExpect(jsonPath("$.detail").value("The embedding provider returned HTTP 401."))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("nope"))));
    }

    @Test
    void statusOmitsTheReasonWhenRetrievalIsUp() throws Exception {
        when(ragStatus.info()).thenReturn(new RagInfo(true, "transformers",
                "paraphrase-multilingual-MiniLM-L12-v2", 384, "pgvector", null));

        mockMvc.perform(get("/api/rag"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.embeddingProvider").value("transformers"))
                .andExpect(jsonPath("$.reason").doesNotExist());
    }
}
