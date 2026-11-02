package io.github.micha1805.llmgateway.rag;

import java.util.List;

import io.github.micha1805.llmgateway.api.AskReply;
import io.github.micha1805.llmgateway.api.AskRequest;
import io.github.micha1805.llmgateway.api.ChatReply;
import io.github.micha1805.llmgateway.api.ChatRequest;
import io.github.micha1805.llmgateway.chat.ChatService;
import io.github.micha1805.llmgateway.chat.ProviderRegistry;
import io.github.micha1805.llmgateway.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private final RagStatus status;

    private final RagProperties properties;

    private final ProviderRegistry registry;

    private final ChatService chatService;

    private final ObjectProvider<VectorStore> vectorStores;

    public RagService(RagStatus status, RagProperties properties, ProviderRegistry registry, ChatService chatService,
            ObjectProvider<VectorStore> vectorStores) {
        this.status = status;
        this.properties = properties;
        this.registry = registry;
        this.chatService = chatService;
        this.vectorStores = vectorStores;
    }

    public AskReply ask(AskRequest request) {
        status.requireAvailable();
        registry.requireAvailable(request.provider());
        int topK = request.topK() == null ? properties.defaultTopK() : request.topK();
        VectorStore store = vectorStores.getIfAvailable();
        if (store == null) {
            throw new RagUnavailableException(status.reason());
        }
        List<Document> hits;
        try {
            hits = store.similaritySearch(SearchRequest.builder()
                    .query(request.question())
                    .topK(topK)
                    .similarityThreshold(properties.similarityThreshold())
                    .build());
        }
        catch (RuntimeException exception) {
            throw EmbeddingFailures.translate(exception);
        }
        if (hits == null) {
            hits = List.of();
        }
        String prompt = GroundedPrompt.build(request.question(), hits);
        ChatReply reply = chatService.chat(new ChatRequest(prompt, request.provider(), request.model()));
        log.info("Ask completed provider={} model={} sources={}", reply.provider().id(), reply.model(), hits.size());
        return new AskReply(reply.provider(), reply.model(), reply.answer(),
                hits.stream().map(Citations::from).toList());
    }
}
