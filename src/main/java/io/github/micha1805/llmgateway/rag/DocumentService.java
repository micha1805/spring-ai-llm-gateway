package io.github.micha1805.llmgateway.rag;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.micha1805.llmgateway.api.DocumentSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private static final int MAX_BYTES = 10 * 1024 * 1024;

    private final RagStatus status;

    private final DocumentExtractor extractor;

    private final DocumentChunker chunker;

    private final ObjectProvider<DocumentCatalog> catalogs;

    private final ObjectProvider<VectorStore> vectorStores;

    private final ObjectProvider<TransactionTemplate> transactions;

    public DocumentService(RagStatus status, DocumentExtractor extractor, DocumentChunker chunker,
            ObjectProvider<DocumentCatalog> catalogs, ObjectProvider<VectorStore> vectorStores,
            ObjectProvider<TransactionTemplate> transactions) {
        this.status = status;
        this.extractor = extractor;
        this.chunker = chunker;
        this.catalogs = catalogs;
        this.vectorStores = vectorStores;
        this.transactions = transactions;
    }

    public IngestResult ingest(String filename, String contentType, byte[] bytes, String source) {
        status.requireAvailable();
        String name = DocumentNames.filename(filename);
        String origin = DocumentNames.source(source);
        if (bytes == null || bytes.length == 0) {
            throw new BadDocumentException("The file is empty.");
        }
        if (bytes.length > MAX_BYTES) {
            throw new BadDocumentException("The file is larger than 10 MB.");
        }
        String hash = DocumentCatalog.sha256(bytes);
        DocumentCatalog catalog = requireCatalog();
        Optional<DocumentSummary> existing = catalog.findByHash(hash);
        if (existing.isPresent()) {
            log.info("Document already ingested id={} name={}", existing.get().id(), existing.get().name());
            return new IngestResult(existing.get(), false);
        }
        DocumentKind kind = DocumentKind.detect(name, contentType);
        List<PageText> pages = extractor.extract(kind, name, bytes);
        UUID id = UUID.randomUUID();
        List<Document> chunks = chunker.chunk(id, name, origin, kind.mediaType(), pages);
        if (chunks.isEmpty()) {
            throw new BadDocumentException("The document has no extractable text.");
        }
        DocumentSummary summary = new DocumentSummary(id, name, kind.mediaType(), origin, bytes.length, chunks.size(),
                java.time.Instant.now());
        try {
            requireTransaction().executeWithoutResult(tx -> {
                catalog.insert(summary, hash);
                requireVectorStore().add(chunks);
            });
        }
        catch (RuntimeException exception) {
            throw EmbeddingFailures.translate(exception);
        }
        log.info("Ingested document id={} name={} chunks={} bytes={}", id, name, chunks.size(), bytes.length);
        return new IngestResult(summary, true);
    }

    public List<DocumentSummary> list() {
        status.requireAvailable();
        return requireCatalog().list();
    }

    public DocumentSummary get(UUID id) {
        status.requireAvailable();
        return requireCatalog().findById(id).orElseThrow(() -> new DocumentNotFoundException(id));
    }

    public void delete(UUID id) {
        status.requireAvailable();
        DocumentCatalog catalog = requireCatalog();
        if (catalog.findById(id).isEmpty()) {
            throw new DocumentNotFoundException(id);
        }
        FilterExpressionBuilder filters = new FilterExpressionBuilder();
        try {
            requireTransaction().executeWithoutResult(tx -> {
                requireVectorStore().delete(filters.eq(ChunkMetadata.DOCUMENT_ID, id.toString()).build());
                if (catalog.delete(id) == 0) {
                    throw new DocumentNotFoundException(id);
                }
            });
        }
        catch (DocumentNotFoundException exception) {
            throw exception;
        }
        catch (RuntimeException exception) {
            throw EmbeddingFailures.translate(exception);
        }
        log.info("Deleted document id={}", id);
    }

    private DocumentCatalog requireCatalog() {
        DocumentCatalog catalog = catalogs.getIfAvailable();
        if (catalog == null) {
            throw new RagUnavailableException(status.reason());
        }
        return catalog;
    }

    private VectorStore requireVectorStore() {
        VectorStore store = vectorStores.getIfAvailable();
        if (store == null) {
            throw new RagUnavailableException(status.reason());
        }
        return store;
    }

    private TransactionTemplate requireTransaction() {
        TransactionTemplate template = transactions.getIfAvailable();
        if (template == null) {
            throw new RagUnavailableException(status.reason());
        }
        return template;
    }
}
