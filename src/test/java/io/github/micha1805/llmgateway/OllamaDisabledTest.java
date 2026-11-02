package io.github.micha1805.llmgateway;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "llm.providers.ollama.enabled=false")
class OllamaDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void disabledOllamaIsUnavailable() throws Exception {
        mockMvc.perform(get("/api/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[4].id").value("ollama"))
                .andExpect(jsonPath("$[4].available").value(false))
                .andExpect(jsonPath("$[4].reason").value(containsString("OLLAMA_ENABLED")));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"ollama","message":"Hello"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value(containsString("OLLAMA_ENABLED")));
    }
}
