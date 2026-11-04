package io.github.micha1805.llmgateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class LlmGatewayApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void startsWithNoKeysAndListsEveryProvider() throws Exception {
        mockMvc.perform(get("/api/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].id").value("openai"))
                .andExpect(jsonPath("$[0].available").value(false))
                .andExpect(jsonPath("$[0].defaultModel").value("gpt-4o-mini"))
                .andExpect(jsonPath("$[0].configurationEnv").value("OPENAI_API_KEY"))
                .andExpect(jsonPath("$[0].reason").value(containsString("OPENAI_API_KEY")))
                .andExpect(jsonPath("$[1].id").value("anthropic"))
                .andExpect(jsonPath("$[1].available").value(false))
                .andExpect(jsonPath("$[1].defaultModel").value("claude-haiku-4-5"))
                .andExpect(jsonPath("$[2].id").value("grok"))
                .andExpect(jsonPath("$[2].available").value(false))
                .andExpect(jsonPath("$[2].defaultModel").value("grok-4.7"))
                .andExpect(jsonPath("$[2].baseUrl").value("https://api.x.ai"))
                .andExpect(jsonPath("$[2].reason").value(containsString("XAI_API_KEY")))
                .andExpect(jsonPath("$[3].id").value("mistral"))
                .andExpect(jsonPath("$[3].available").value(false))
                .andExpect(jsonPath("$[4].id").value("ollama"))
                .andExpect(jsonPath("$[4].available").value(true))
                .andExpect(jsonPath("$[4].defaultModel").value("llama3.2"))
                .andExpect(jsonPath("$[4].reason").doesNotExist());
    }

    @Test
    void chatWithoutAKeyIsAClear503() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"openai","message":"Explain virtual threads"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Provider not available"))
                .andExpect(jsonPath("$.detail").value(containsString("OPENAI_API_KEY")))
                .andExpect(jsonPath("$.provider").value("openai"))
                .andExpect(jsonPath("$.instance").value("/api/chat"));
    }

    @Test
    void swaggerUiAndOpenApiArePublished() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("swagger-ui")));

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Swagger UI")));

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("LLM Gateway"))
                .andExpect(jsonPath("$.paths['/api/chat'].post").exists())
                .andExpect(jsonPath("$.paths['/api/providers'].get").exists())
                .andExpect(jsonPath("$.paths['/api/ask'].post").exists())
                .andExpect(jsonPath("$.paths['/api/documents'].post").exists())
                .andExpect(jsonPath("$.paths['/api/documents'].get").exists())
                .andExpect(jsonPath("$.paths['/api/documents/{id}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/rag'].get").exists())
                .andExpect(content().string(containsString("Explain virtual threads")))
                .andExpect(content().string(containsString("forfait journalier fictif")));

        mockMvc.perform(get("/"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/swagger-ui.html"));
    }

    @Test
    void providerCatalogDoesNotEchoSecrets() throws Exception {
        String body = mockMvc.perform(get("/api/providers"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(body).doesNotContain("apiKey").doesNotContain("sk-");
    }

    @Test
    void ragRoutesExplainAMissingDatabase() throws Exception {
        mockMvc.perform(get("/api/rag"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.embeddingProvider").value("transformers"))
                .andExpect(jsonPath("$.embeddingModel").value("paraphrase-multilingual-MiniLM-L12-v2"))
                .andExpect(jsonPath("$.dimensions").value(384))
                .andExpect(jsonPath("$.reason").value(containsString("SPRING_DATASOURCE_URL")));

        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"grok","question":"Quel est le forfait journalier fictif ?"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Retrieval not available"))
                .andExpect(jsonPath("$.detail").value(containsString("SPRING_DATASOURCE_URL")))
                .andExpect(jsonPath("$.instance").value("/api/ask"));

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Retrieval not available"));
    }
}
