package io.github.micha1805.llmgateway.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

@Component
@Order(100)
@ConditionalOnProperty(prefix = "llm.rag.samples", name = "enabled", havingValue = "true")
public class SampleDocumentLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleDocumentLoader.class);

    private final RagStatus status;

    private final DocumentService documents;

    public SampleDocumentLoader(RagStatus status, DocumentService documents) {
        this.status = status;
        this.documents = documents;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!status.available()) {
            log.warn("Sample documents were not loaded: {}", status.reason());
            return;
        }
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath:samples/mutualite/*");
        for (Resource resource : resources) {
            if (!resource.isReadable() || resource.getFilename() == null) {
                continue;
            }
            try {
                IngestResult result = documents.ingest(resource.getFilename(), null,
                        resource.getContentAsByteArray(), "sample");
                log.info("Sample document {} {}", result.created() ? "loaded" : "already present",
                        result.document().name());
            }
            catch (RagUnavailableException exception) {
                log.warn("Sample documents were not loaded: {}", exception.getMessage());
                return;
            }
            catch (RuntimeException exception) {
                log.error("Sample document {} was not loaded", resource.getFilename(), exception);
            }
        }
    }
}
