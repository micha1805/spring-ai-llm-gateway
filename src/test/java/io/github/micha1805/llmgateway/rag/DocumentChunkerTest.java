package io.github.micha1805.llmgateway.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import io.github.micha1805.llmgateway.config.RagProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

class DocumentChunkerTest {

    private final DocumentExtractor extractor = new DocumentExtractor();

    private final DocumentChunker chunker = new DocumentChunker(new RagProperties(4, 0.3, 32, 20,
            new RagProperties.Samples(false)));

    @Test
    void markdownKeepsTheDocumentNameAndOmitsThePage() {
        String text = """
                # Consultations

                Le ticket moderateur fictif est de 4 euros.

                # Specialiste

                L'avantage complementaire fictif est de 12 euros pour le specialiste conventionne de la mutuelle exemple.
                """;
        UUID id = UUID.randomUUID();
        List<Document> chunks = chunker.chunk(id, "consultations-fr.md", "sample", "text/markdown",
                extractor.extract(DocumentKind.MARKDOWN, "consultations-fr.md", text.getBytes()));

        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.getMetadata()).containsEntry(ChunkMetadata.DOCUMENT_ID, id.toString());
            assertThat(chunk.getMetadata()).containsEntry(ChunkMetadata.DOCUMENT_NAME, "consultations-fr.md");
            assertThat(chunk.getMetadata()).containsEntry(ChunkMetadata.SOURCE, "sample");
            assertThat(chunk.getMetadata()).doesNotContainKey(ChunkMetadata.PAGE);
        });
        assertThat(chunks.get(0).getMetadata()).containsEntry(ChunkMetadata.CHUNK, 0);
        assertThat(chunks.get(chunks.size() - 1).getMetadata()).containsEntry(ChunkMetadata.CHUNK, chunks.size() - 1);
    }

    @Test
    void pdfKeepsThePageNumber() throws Exception {
        byte[] pdf = new ClassPathResource("samples/mutualite/hospitalisation-fr.pdf").getContentAsByteArray();
        List<PageText> pages = extractor.extract(DocumentKind.PDF, "hospitalisation-fr.pdf", pdf);

        assertThat(pages).extracting(PageText::page).containsExactly(1, 2);
        assertThat(pages.get(0).text()).contains("47 euros");
        assertThat(pages.get(1).text()).contains("35 euros");

        List<Document> chunks = chunker.chunk(UUID.randomUUID(), "hospitalisation-fr.pdf", "sample",
                "application/pdf", pages);
        assertThat(chunks).anySatisfy(chunk ->
                assertThat(chunk.getMetadata()).containsEntry(ChunkMetadata.PAGE, 1));
        assertThat(chunks).anySatisfy(chunk ->
                assertThat(chunk.getMetadata()).containsEntry(ChunkMetadata.PAGE, 2));
    }

    @Test
    void rejectsAnUnknownExtension() {
        assertThatThrownBy(() -> DocumentKind.detect("notes.docx", "application/octet-stream"))
                .isInstanceOf(UnsupportedDocumentException.class);
    }

    @Test
    void stripsAPathFromTheFileName() {
        assertThat(DocumentNames.filename("C:\\docs\\note.md")).isEqualTo("note.md");
        assertThat(DocumentNames.filename("samples/note.md")).isEqualTo("note.md");
    }

    @Test
    void textMustBeUtf8() {
        assertThatThrownBy(() -> extractor.extract(DocumentKind.TEXT, "bad.txt", new byte[] {(byte) 0xff}))
                .isInstanceOf(BadDocumentException.class)
                .hasMessageContaining("UTF-8");
    }

    @Test
    void byteArrayResourceIsReadable() {
        assertThat(new ByteArrayResource("hello".getBytes()).getByteArray()).hasSize(5);
    }
}
