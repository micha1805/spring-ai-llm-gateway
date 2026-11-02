package io.github.micha1805.llmgateway.rag;

import java.io.IOException;

import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.core.io.Resource;

final class ClosingPagePdfReader extends PagePdfDocumentReader implements AutoCloseable {

    ClosingPagePdfReader(Resource resource) {
        super(resource, PdfDocumentReaderConfig.builder().withPagesPerDocument(1).build());
    }

    @Override
    public void close() throws IOException {
        document.close();
    }
}
