package io.github.micha1805.llmgateway.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.micha1805.llmgateway.api.AskReply;
import io.github.micha1805.llmgateway.api.AskRequest;
import io.github.micha1805.llmgateway.api.ChatReply;
import io.github.micha1805.llmgateway.api.ChatRequest;
import io.github.micha1805.llmgateway.chat.ChatService;
import io.github.micha1805.llmgateway.chat.ProviderId;
import io.github.micha1805.llmgateway.chat.ProviderRegistry;
import io.github.micha1805.llmgateway.chat.ProviderUnavailableException;
import io.github.micha1805.llmgateway.chat.UnavailableChatProvider;
import io.github.micha1805.llmgateway.config.RagProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.client.ResourceAccessException;

class RagServiceTest {

    @Test
    void answersFromRetrievedChunksAndCitesThem() {
        UUID documentId = UUID.randomUUID();
        Document hit = Document.builder()
                .id("chunk-1")
                .text("Avantage fictif de 6 euros.")
                .metadata(Map.of(
                        ChunkMetadata.DOCUMENT_ID, documentId.toString(),
                        ChunkMetadata.DOCUMENT_NAME, "consultations-fr.md",
                        ChunkMetadata.CHUNK, 1,
                        ChunkMetadata.SOURCE, "sample"))
                .score(0.8123456)
                .build();
        VectorStore store = org.mockito.Mockito.mock(VectorStore.class);
        when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(hit));

        ChatService chat = org.mockito.Mockito.mock(ChatService.class);
        when(chat.chat(any(ChatRequest.class))).thenAnswer(invocation -> {
            ChatRequest request = invocation.getArgument(0);
            assertThat(request.message()).contains("only the excerpts");
            assertThat(request.message()).contains("Avantage fictif de 6 euros.");
            assertThat(request.message()).contains("Reply in the same language");
            assertThat(request.provider()).isEqualTo(ProviderId.GROK);
            assertThat(request.model()).isEqualTo("grok-4.7");
            return new ChatReply(ProviderId.GROK, "grok-4.7", "L'avantage fictif est de 6 euros.");
        });

        AskReply reply = service(store, chat, registryWith(ProviderId.GROK))
                .ask(new AskRequest("Quel est l'avantage ?", ProviderId.GROK, "grok-4.7", 3));

        assertThat(reply.answer()).isEqualTo("L'avantage fictif est de 6 euros.");
        assertThat(reply.sources()).hasSize(1);
        assertThat(reply.sources().get(0).documentId()).isEqualTo(documentId);
        assertThat(reply.sources().get(0).documentName()).isEqualTo("consultations-fr.md");
        assertThat(reply.sources().get(0).page()).isNull();
        assertThat(reply.sources().get(0).chunk()).isEqualTo(1);
        assertThat(reply.sources().get(0).snippet()).contains("6 euros");
        assertThat(reply.sources().get(0).score()).isEqualTo(0.812346);
    }

    @Test
    void dropsANeighbourBelowTheSimilarityFloor() {
        Document weak = Document.builder()
                .id("chunk-1")
                .text("Some other paragraph.")
                .metadata(Map.of(
                        ChunkMetadata.DOCUMENT_ID, UUID.randomUUID().toString(),
                        ChunkMetadata.DOCUMENT_NAME, "other.txt",
                        ChunkMetadata.CHUNK, 0))
                .score(0.05)
                .build();
        VectorStore store = org.mockito.Mockito.mock(VectorStore.class);
        when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(weak));
        ChatService chat = org.mockito.Mockito.mock(ChatService.class);
        when(chat.chat(any(ChatRequest.class))).thenAnswer(invocation -> {
            ChatRequest request = invocation.getArgument(0);
            assertThat(request.message()).contains("(no excerpts were retrieved)");
            return new ChatReply(ProviderId.GROK, "grok-4.7", "Not in the documents.");
        });

        AskReply reply = new RagService(
                availableStatus(),
                new RagProperties(4, 0.10, 120, 40, new RagProperties.Samples(false)),
                registryWith(ProviderId.GROK),
                chat,
                providerOf(store))
                .ask(new AskRequest("Who is Camille Dupont?", ProviderId.GROK, null, 4));

        assertThat(reply.sources()).isEmpty();
    }

    @Test
    void tellsTheModelWhenNothingWasRetrieved() {
        VectorStore store = org.mockito.Mockito.mock(VectorStore.class);
        when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        ChatService chat = org.mockito.Mockito.mock(ChatService.class);
        when(chat.chat(any(ChatRequest.class))).thenAnswer(invocation -> {
            ChatRequest request = invocation.getArgument(0);
            assertThat(request.message()).contains("(no excerpts were retrieved)");
            assertThat(request.message()).contains("not in the documents");
            return new ChatReply(ProviderId.OLLAMA, "llama3.2", "The answer is not in the documents.");
        });

        AskReply reply = service(store, chat, registryWith(ProviderId.OLLAMA))
                .ask(new AskRequest("What is the daily rate?", ProviderId.OLLAMA, null, null));

        assertThat(reply.sources()).isEmpty();
        assertThat(reply.answer()).contains("not in the documents");
    }

    @Test
    void doesNotSearchWhenTheChatProviderIsMissing() {
        VectorStore store = org.mockito.Mockito.mock(VectorStore.class);
        RagService service = service(store, org.mockito.Mockito.mock(ChatService.class), registryWith(ProviderId.OLLAMA));

        assertThatThrownBy(() -> service.ask(new AskRequest("Bonjour", ProviderId.OPENAI, null, null)))
                .isInstanceOf(ProviderUnavailableException.class)
                .hasMessageContaining("OPENAI_API_KEY");
        verify(store, never()).similaritySearch(any(SearchRequest.class));
    }

    @Test
    void embeddingConnectionFailuresDoNotEchoTheQuestion() {
        VectorStore store = org.mockito.Mockito.mock(VectorStore.class);
        when(store.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        assertThatThrownBy(() -> service(store, org.mockito.Mockito.mock(ChatService.class), registryWith(ProviderId.GROK))
                .ask(new AskRequest("secret question about reimbursements", ProviderId.GROK, null, 2)))
                .isInstanceOf(EmbeddingFailedException.class)
                .hasMessageContaining("Could not reach the embedding provider")
                .hasMessageNotContaining("secret question");
    }

    private static RagStatus availableStatus() {
        RagStatus status = org.mockito.Mockito.mock(RagStatus.class);
        when(status.available()).thenReturn(true);
        return status;
    }

    private static RagService service(VectorStore store, ChatService chat, ProviderRegistry registry) {
        RagStatus status = availableStatus();
        ObjectProvider<VectorStore> stores = providerOf(store);
        return new RagService(status, new RagProperties(4, 0.0, 120, 40, new RagProperties.Samples(false)),
                registry, chat, stores);
    }

    private static ProviderRegistry registryWith(ProviderId available) {
        java.util.Map<ProviderId, io.github.micha1805.llmgateway.chat.ChatProvider> providers =
                new java.util.EnumMap<>(ProviderId.class);
        for (ProviderId id : ProviderId.values()) {
            providers.put(id, UnavailableChatProvider.missingKey(id, "default", "https://example.invalid"));
        }
        io.github.micha1805.llmgateway.chat.ChatProvider ready =
                org.mockito.Mockito.mock(io.github.micha1805.llmgateway.chat.ChatProvider.class);
        when(ready.id()).thenReturn(available);
        when(ready.available()).thenReturn(true);
        when(ready.defaultModel()).thenReturn("default");
        when(ready.baseUrl()).thenReturn("https://example.invalid");
        providers.put(available, ready);
        return new ProviderRegistry(providers.values());
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<VectorStore> providerOf(VectorStore store) {
        ObjectProvider<VectorStore> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(store);
        return provider;
    }
}
