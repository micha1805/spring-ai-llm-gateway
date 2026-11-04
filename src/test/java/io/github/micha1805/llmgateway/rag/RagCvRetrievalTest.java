package io.github.micha1805.llmgateway.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Real multilingual MiniLM embeddings against pgvector, with the production similarity
 * floor. A 120-token chunk that mentions a fictional person once scores about 0.12-0.22,
 * which the old 0.30 floor discarded.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class RagCvRetrievalTest {

    private static final HttpServer OLLAMA = startOllama();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("llm.rag.samples.enabled", () -> "false");
        // One chunk longer than the model's 128-token window, so mean-pooling dilutes the name
        // the way a full CV page does. The splitter must not cut that page into a name-only span.
        registry.add("llm.rag.chunk-size", () -> "400");
        registry.add("llm.embeddings.transformers.cache-directory", () -> "/tmp/llm-gateway-onnx");
        registry.add("llm.providers.ollama.base-url",
                () -> "http://127.0.0.1:" + OLLAMA.getAddress().getPort());
    }

    @Autowired
    private MockMvc mockMvc;

    @AfterAll
    static void stopOllama() {
        OLLAMA.stop(0);
    }

    @Test
    void englishAndFrenchQuestionsReturnTheFictionalCv() throws Exception {
        String cv = fictionalCv();
        mockMvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "camille-dupont-cv.txt", "text/plain",
                                cv.getBytes(StandardCharsets.UTF_8)))
                        .param("source", "cv"))
                .andExpect(status().isCreated());

        ask("Who is Camille Dupont?");
        ask("Qui est Camille Dupont ?");
    }

    private void ask(String question) throws Exception {
        String body = mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"ollama","question":"%s","topK":4}
                                """.formatted(question.replace("\"", ""))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources[*].documentName", hasItem("camille-dupont-cv.txt")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(body).contains("camille-dupont-cv.txt");
    }

    /**
     * The name and the project sit in the first sentence. The rest is long enough that
     * mean-pooling over the 128-token window pulls cosine similarity under 0.30.
     */
    private static String fictionalCv() {
        String duty = "Delivered REST services, reviewed pull requests, wrote integration tests, "
                + "and operated PostgreSQL schemas for internal products. ";
        return duty.repeat(3)
                + "Camille Dupont worked on the fictional catalog Quasiflora in 2019. "
                + duty.repeat(3);
    }

    private static HttpServer startOllama() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/chat", exchange -> {
                byte[] response = """
                        {"model":"llama3.2","message":{"role":"assistant","content":"Fictional answer."},"done":true}
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
}
