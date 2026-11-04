package io.github.micha1805.llmgateway.rag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.micha1805.llmgateway.config.RagProperties;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DocumentChunker {

    private final RagProperties properties;

    public DocumentChunker(RagProperties properties) {
        this.properties = properties;
    }

    public List<Document> chunk(UUID documentId, String name, String source, String mediaType, List<PageText> pages) {
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(properties.chunkSize())
                .withMinChunkSizeChars(properties.minChunkSizeChars())
                .withMinChunkLengthToEmbed(5)
                .withMaxNumChunks(10_000)
                .withKeepSeparator(true)
                .build();
        List<Document> chunks = new ArrayList<>();
        int index = 0;
        for (PageText page : pages) {
            if (!StringUtils.hasText(page.text())) {
                continue;
            }
            List<Document> pieces = splitter.apply(List.of(new Document(page.text())));
            for (Document piece : pieces) {
                if (!StringUtils.hasText(piece.getText())) {
                    continue;
                }
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put(ChunkMetadata.DOCUMENT_ID, documentId.toString());
                metadata.put(ChunkMetadata.DOCUMENT_NAME, name);
                metadata.put(ChunkMetadata.SOURCE, source);
                metadata.put(ChunkMetadata.CHUNK, index);
                metadata.put(ChunkMetadata.MEDIA_TYPE, mediaType);
                if (page.page() != null) {
                    metadata.put(ChunkMetadata.PAGE, page.page());
                }
                chunks.add(new Document(UUID.randomUUID().toString(), piece.getText(), metadata));
                index++;
            }
        }
        return chunks;
    }
}
