package io.github.micha1805.llmgateway.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import io.github.micha1805.llmgateway.config.RagProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
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
    void pdfKeepsLigaturesThatTheLayoutReaderWouldDrop() {
        byte[] pdf = ligaturePdf();
        List<PageText> pages = extractor.extract(DocumentKind.PDF, "ligatures.pdf", pdf);

        assertThat(pages).hasSize(1);
        assertThat(pages.get(0).page()).isEqualTo(1);
        assertThat(pages.get(0).text()).contains("Native").contains("first");
        assertThat(pages.get(0).text()).doesNotContain("Natve").doesNotContain("frst");
    }

    @Test
    void pdfNormalizesCompatibilityLigatures() {
        assertThat(DocumentExtractor.normalizePdfText("ef\uFB01cient \uFB02ow")).isEqualTo("efficient flow");
    }

    /**
     * One glyph whose ToUnicode CMap expands to two characters ("ti", "fi"). The layout
     * stripper kept only the first character ("Natve", "frst"). PDFTextStripper keeps both.
     */
    private static byte[] ligaturePdf() {
        String cmap = """
                /CIDInit /ProcSet findresource begin
                12 dict begin
                begincmap
                /CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def
                /CMapName /Adobe-Identity-UCS def
                /CMapType 2 def
                1 begincodespacerange
                <00> <FF>
                endcodespacerange
                2 beginbfchar
                <01> <00740069>
                <02> <00660069>
                endbfchar
                endcmap
                CMapName currentdict /CMap defineresource pop
                end
                end
                """;
        String content = "BT /F1 12 Tf 72 720 Td (Na) Tj <01> Tj (ve ) Tj <02> Tj (rst) Tj ET\n";
        byte[] contentBytes = content.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        byte[] cmapBytes = cmap.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        byte[][] objects = {
                "<< /Type /Catalog /Pages 2 0 R >>".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>"
                        .getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                ("<< /Length " + contentBytes.length + " >>\nstream\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding /ToUnicode 6 0 R >>"
                        .getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                ("<< /Length " + cmapBytes.length + " >>\nstream\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII)
        };
        byte[][] tails = {
                contentBytes,
                cmapBytes
        };
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try {
            out.write("%PDF-1.4\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            int[] offsets = new int[6];
            for (int i = 0; i < 6; i++) {
                offsets[i] = out.size();
                out.write((i + 1 + " 0 obj\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                out.write(objects[i]);
                if (i == 3) {
                    out.write(tails[0]);
                    out.write("endstream".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                }
                if (i == 5) {
                    out.write(tails[1]);
                    out.write("endstream".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                }
                out.write("\nendobj\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            }
            int xref = out.size();
            out.write("xref\n0 7\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            out.write("0000000000 65535 f \n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            for (int offset : offsets) {
                out.write(String.format(java.util.Locale.ROOT, "%010d 00000 n \n", offset)
                        .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            }
            out.write(("trailer\n<< /Size 7 /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n")
                    .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        }
        catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
        return out.toByteArray();
    }
}
