package io.github.micha1805.llmgateway.rag;

public final class UnsupportedDocumentException extends RuntimeException {

    public UnsupportedDocumentException(String filename) {
        super("'" + filename + "' is not a supported document. Upload a PDF, text, or Markdown file (.pdf, .txt, .md).");
    }
}
