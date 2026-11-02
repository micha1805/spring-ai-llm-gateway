package io.github.micha1805.llmgateway.rag;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DocumentExtractor {

    private static final Logger log = LoggerFactory.getLogger(DocumentExtractor.class);

    public List<PageText> extract(DocumentKind kind, String filename, byte[] bytes) {
        return switch (kind) {
            case PDF -> pdf(filename, bytes);
            case TEXT -> List.of(new PageText(null, utf8(bytes)));
            case MARKDOWN -> markdown(utf8(bytes));
        };
    }

    private static List<PageText> pdf(String filename, byte[] bytes) {
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        try (ClosingPagePdfReader reader = new ClosingPagePdfReader(resource)) {
            List<PageText> pages = new ArrayList<>();
            for (Document document : reader.get()) {
                if (!StringUtils.hasText(document.getText())) {
                    continue;
                }
                Object page = document.getMetadata().get(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER);
                Integer pageNumber = page instanceof Number number ? number.intValue() : null;
                pages.add(new PageText(pageNumber, tidyPdfText(document.getText())));
            }
            return pages;
        }
        catch (BadDocumentException exception) {
            throw exception;
        }
        catch (RuntimeException | java.io.IOException exception) {
            log.warn("Could not read PDF {}", filename, exception);
            throw new BadDocumentException("The file could not be read as a PDF.");
        }
    }

    private static List<PageText> markdown(String text) {
        List<PageText> sections = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : text.split("\\R", -1)) {
            if (line.startsWith("#") && !current.isEmpty()) {
                sections.add(new PageText(null, current.toString()));
                current.setLength(0);
            }
            current.append(line).append('\n');
        }
        if (!current.toString().isBlank()) {
            sections.add(new PageText(null, current.toString()));
        }
        return sections;
    }

    /**
     * The layout stripper keeps visual gaps as runs of spaces. Collapse them so a chunk
     * reads as the sentence that was written, not as a positioning grid.
     */
    private static String tidyPdfText(String text) {
        return text.replace('\u00A0', ' ')
                .replaceAll("[ \\t]{2,}", " ")
                .replaceAll(" *\\R *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }

    private static String utf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        }
        catch (CharacterCodingException exception) {
            throw new BadDocumentException("Text and Markdown files must be UTF-8.");
        }
    }
}
