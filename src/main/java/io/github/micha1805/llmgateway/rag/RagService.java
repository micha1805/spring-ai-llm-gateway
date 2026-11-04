package io.github.micha1805.llmgateway.rag;

import java.util.List;
import java.util.Locale;

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
        // Ask pgvector for the nearest neighbours with no cutoff, then apply the configured
        // cosine-similarity floor here. Filtering inside the store hid the scores that were
        // just under the old 0.30 default, which made an empty source list undiagnosable.
        double threshold = properties.similarityThreshold();
        List<Document> candidates;
        try {
            candidates = store.similaritySearch(SearchRequest.builder()
                    .query(request.question())
                    .topK(topK)
                    .similarityThreshold(0.0)
                    .build());
        }
        catch (RuntimeException exception) {
            throw EmbeddingFailures.translate(exception);
        }
        if (candidates == null) {
            candidates = List.of();
        }
        List<Document> hits = candidates.stream()
                .filter(document -> scoreOf(document) >= threshold)
                .toList();
        logRetrieval(topK, threshold, candidates);
        String prompt = GroundedPrompt.build(request.question(), hits);
        ChatReply reply = chatService.chat(new ChatRequest(prompt, request.provider(), request.model()));
        log.info("Ask completed provider={} model={} sources={}", reply.provider().id(), reply.model(), hits.size());
        return new AskReply(reply.provider(), reply.model(), reply.answer(),
                hits.stream().map(Citations::from).toList());
    }

    private static void logRetrieval(int topK, double threshold, List<Document> candidates) {
        long kept = candidates.stream().filter(document -> scoreOf(document) >= threshold).count();
        log.info("Ask retrieval topK={} threshold={} candidates={} kept={}", topK, threshold, candidates.size(), kept);
        for (Document candidate : candidates) {
            double score = scoreOf(candidate);
            log.info("Ask candidate document={} chunk={} page={} score={} kept={}",
                    candidate.getMetadata().get(ChunkMetadata.DOCUMENT_NAME),
                    candidate.getMetadata().get(ChunkMetadata.CHUNK),
                    candidate.getMetadata().get(ChunkMetadata.PAGE),
                    String.format(Locale.ROOT, "%.4f", score),
                    score >= threshold);
        }
    }

    private static double scoreOf(Document document) {
        Double score = document.getScore();
        return score == null ? 0.0 : score;
    }
}
