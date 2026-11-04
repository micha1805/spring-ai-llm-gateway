package io.github.micha1805.llmgateway.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import io.github.micha1805.llmgateway.config.EmbeddingProperties;
import org.springframework.ai.embedding.EmbeddingModel;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(RagVectorStoreIntegrationTest.DeterministicEmbeddings.class)
class RagVectorStoreIntegrationTest {

    private static final AtomicReference<String> LAST_CHAT_BODY = new AtomicReference<>();

    private static final HttpServer OLLAMA = startOllama();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("llm.embeddings.provider", () -> "deterministic");
        registry.add("llm.embeddings.dimensions", () -> "32");
        registry.add("llm.rag.similarity-threshold", () -> "0");
        registry.add("llm.rag.samples.enabled", () -> "false");
        registry.add("llm.providers.ollama.base-url",
                () -> "http://127.0.0.1:" + OLLAMA.getAddress().getPort());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterAll
    static void stopOllama() {
        OLLAMA.stop(0);
    }

    @Test
    void ingestsSearchesAndDeletesAgainstPgvector() throws Exception {
        mockMvc.perform(get("/api/rag"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.embeddingProvider").value("deterministic"))
                .andExpect(jsonPath("$.dimensions").value(32))
                .andExpect(jsonPath("$.vectorStore").value("pgvector"))
                .andExpect(jsonPath("$.reason").doesNotExist());

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"openai","message":"still works"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Provider not available"));

        String nonce = "zebra-forfait-47-orange";
        String body = """
                FICTIONAL SAMPLE. Mutuelle Exemple.
                Forfait journalier fictif %s : 47 euros par jour, maximum 30 jours.
                """.formatted(nonce);
        String created = mockMvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "forfait.txt", "text/plain",
                                body.getBytes(StandardCharsets.UTF_8)))
                        .param("source", "upload"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("forfait.txt"))
                .andExpect(jsonPath("$.chunkCount").value(1))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(com.jayway.jsonpath.JsonPath.read(created, "$.id"));

        String duplicate = mockMvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "forfait-copy.txt", "text/plain",
                                body.getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(UUID.fromString(com.jayway.jsonpath.JsonPath.read(duplicate, "$.id"))).isEqualTo(id);
        assertThat(chunkCount(id)).isEqualTo(1);

        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"ollama","question":"Quel est le forfait journalier fictif %s ?","topK":2}
                                """.formatted(nonce)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("ollama"))
                .andExpect(jsonPath("$.answer").value("Reponse fictive ancree dans les extraits."))
                .andExpect(jsonPath("$.sources[0].documentId").value(id.toString()))
                .andExpect(jsonPath("$.sources[0].documentName").value("forfait.txt"))
                .andExpect(jsonPath("$.sources[0].chunk").value(0))
                .andExpect(jsonPath("$.sources[0].page").doesNotExist())
                .andExpect(jsonPath("$.sources[0].snippet").value(containsString(nonce)))
                .andExpect(jsonPath("$.sources[0].score").exists());

        assertThat(LAST_CHAT_BODY.get()).contains("only the excerpts");
        assertThat(LAST_CHAT_BODY.get()).contains(nonce);
        assertThat(LAST_CHAT_BODY.get()).doesNotContain("sk-");

        byte[] pdf = new ClassPathResource("samples/mutualite/hospitalisation-fr.pdf").getContentAsByteArray();
        String pdfBody = mockMvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "hospitalisation-fr.pdf", "application/pdf", pdf))
                        .param("source", "sample"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID pdfId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(pdfBody, "$.id"));

        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"ollama","question":"Forfait journalier fictif 47 euros chambre commune","topK":4}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources[?(@.documentName == 'hospitalisation-fr.pdf')].page")
                        .value(org.hamcrest.Matchers.hasItem(1)));

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')].name".formatted(pdfId))
                        .value(org.hamcrest.Matchers.hasItem("hospitalisation-fr.pdf")));

        mockMvc.perform(delete("/api/documents/{id}", id))
                .andExpect(status().isNoContent());
        assertThat(chunkCount(id)).isZero();

        mockMvc.perform(delete("/api/documents/{id}", pdfId))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"ollama","question":"zebra orange token uniquement","topK":2}
                                """))
                .andExpect(status().isOk());
        assertThat(LAST_CHAT_BODY.get()).contains("(no excerpts were retrieved)");

        mockMvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "notes.docx", "application/octet-stream", "nope".getBytes())))
                .andExpect(status().isUnsupportedMediaType());

        mockMvc.perform(get("/api/documents/not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    private int chunkCount(UUID documentId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM vector_store WHERE metadata->>'documentId' = ?",
                Integer.class,
                documentId.toString());
        return count == null ? 0 : count;
    }

    private static HttpServer startOllama() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/chat", exchange -> {
                LAST_CHAT_BODY.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] response = """
                        {"model":"llama3.2","message":{"role":"assistant","content":"Reponse fictive ancree dans les extraits."},"done":true}
                        """.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(response);
                }
            });
            server.start();
            return server;
        }
        catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @TestConfiguration
    static class DeterministicEmbeddings {

        @Bean
        EmbeddingModel embeddingModel(EmbeddingProperties properties) {
            return new DeterministicEmbeddingModel(properties.resolvedDimensions());
        }
    }
}
